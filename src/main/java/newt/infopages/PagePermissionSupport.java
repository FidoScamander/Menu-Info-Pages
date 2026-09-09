package newt.infopages;

import com.hypixel.hytale.server.core.Message;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class PagePermissionSupport {
    static final String DEMO_PERMISSION = "newt.infopages.newpage";
    private static volatile Map<String, String> permissions = Map.of();

    private PagePermissionSupport() {}

    static void reload(Path pagesDirectory) {
        Map<String, String> next = new HashMap<>();
        if (pagesDirectory == null || !Files.isDirectory(pagesDirectory)) {
            permissions = Map.of();
            return;
        }
        try (var stream = Files.list(pagesDirectory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> readPermission(path, next));
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] Could not reload page permissions: " + ex.getMessage());
        }
        permissions = Collections.unmodifiableMap(next);
        long protectedPages = next.values().stream().filter(value -> !value.isBlank()).count();
        if (protectedPages > 0) {
            System.out.println("[MenuInfoPages] Page permissions: " + protectedPages + " protected command(s).");
        }
    }

    @SuppressWarnings("unchecked")
    private static void readPermission(Path path, Map<String, String> target) {
        try {
            Object parsed = SimpleJson.parse(Files.readString(path, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) return;
            String command = map.get("command") instanceof String s ? PageDefinition.normalizeCommand(s) : "";
            String permission = map.get("permission") instanceof String s ? s.strip() : "";
            if (path.getFileName().toString().equalsIgnoreCase("example.json") && command.equals("guide") && permission.isBlank()) {
                permission = DEMO_PERMISSION;
            }
            if (!command.isBlank()) target.put(command, permission);
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] Permission scan ignored " + path.getFileName() + ": " + ex.getMessage());
        }
    }

    static String permissionFor(String command) {
        if (command == null) return "";
        String normalized;
        try { normalized = PageDefinition.normalizeCommand(command); }
        catch (IllegalArgumentException ex) { return ""; }
        return permissions.getOrDefault(normalized, "");
    }

    static String withEmptyPermissionField(String json) {
        if (json == null || json.contains("\"permission\"")) return json;
        int commandEnd = json.indexOf('\n', json.indexOf("\"command\""));
        if (commandEnd < 0) return json;
        return json.substring(0, commandEnd + 1) + "  \"permission\": \"\",\n" + json.substring(commandEnd + 1);
    }

    static boolean canAccess(Object sender, String command) {
        String permission = permissionFor(command);
        if (permission.isBlank()) return true;
        if (sender == null) return false;
        for (String methodName : new String[]{"hasPermission", "hasPermissionNode"}) {
            try {
                Method method = sender.getClass().getMethod(methodName, String.class);
                Object result = method.invoke(sender, permission);
                if (result instanceof Boolean allowed) return allowed;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            Object module = Class.forName("com.hypixel.hytale.server.core.permissions.PermissionsModule")
                    .getMethod("get").invoke(null);
            for (Method method : module.getClass().getMethods()) {
                if (!method.getName().equals("hasPermission") || method.getParameterCount() != 2) continue;
                Object result = method.invoke(module, sender, permission);
                if (result instanceof Boolean allowed) return allowed;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    static boolean canAccessOrNotify(Object sender, String command) {
        if (canAccess(sender, command)) return true;
        sendDenied(sender);
        return false;
    }

    static boolean manualOpenAllowed(Object player, Object playerRef, String command) {
        if (canAccess(player, command) || canAccess(playerRef, command)) return true;
        sendDenied(playerRef != null ? playerRef : player);
        return false;
    }

    static boolean autoOpenAllowed(boolean notify, Object player, String command) {
        boolean allowed = canAccess(player, command);
        if (!allowed && notify) sendDenied(player);
        return allowed;
    }

    private static void sendDenied(Object receiver) {
        if (receiver == null) return;
        try {
            Method send = receiver.getClass().getMethod("sendMessage", Message.class);
            send.invoke(receiver, Message.raw(PermissionDeniedLocalization.text(receiver)));
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
