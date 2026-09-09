package newt.infopages;

import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class AutoOpenConfig {
    final boolean enabled;
    final String mode;
    final int version;
    final List<String> worlds;
    final int delayMs;

    AutoOpenConfig(boolean enabled, String mode, int version, List<String> worlds, int delayMs) {
        this.enabled = enabled;
        this.mode = normalizeMode(mode);
        this.version = Math.max(1, version);
        this.worlds = List.copyOf(worlds == null ? List.of() : worlds);
        this.delayMs = Math.max(0, Math.min(delayMs, 60_000));
    }

    static AutoOpenConfig disabled() {
        return new AutoOpenConfig(false, "dismissible", 1, List.of(), 1500);
    }

    static AutoOpenConfig fromPageMap(Map<String, Object> page) {
        Object raw = page == null ? null : page.get("autoOpen");
        if (!(raw instanceof Map<?, ?> values)) return disabled();
        boolean enabled = booleanValue(values.get("enabled"), false);
        String mode = stringValue(values.get("mode"), "dismissible");
        int version = intValue(values.get("version"), 1);
        Object delay = values.get("delayMs") != null ? values.get("delayMs") : values.get("delay-ms");
        int delayMs = intValue(delay, 1500);
        List<String> worlds = new ArrayList<>();
        if (values.get("worlds") instanceof List<?> entries) {
            for (Object entry : entries) if (entry instanceof String s && !s.isBlank()) worlds.add(s.strip());
        }
        return new AutoOpenConfig(enabled, mode, version, worlds, delayMs);
    }

    boolean isOnce() { return mode.equals("once"); }
    boolean isAlways() { return mode.equals("always"); }
    boolean isDismissible() { return mode.equals("dismissible"); }

    boolean matches(World world) {
        if (!enabled || world == null) return false;
        if (worlds.isEmpty()) return true;
        String worldName = safe(world.getName());
        String displayName = "";
        boolean instance = false;
        try {
            var config = world.getWorldConfig();
            if (config != null) {
                displayName = safe(config.getDisplayName());
                instance = InstanceWorldConfig.get(config) != null;
            }
        } catch (Throwable ignored) {
        }
        String generatedName = instance ? stableGeneratedInstanceName(worldName) : "";
        for (String selector : worlds) {
            String requested = safe(selector);
            if (requested.isEmpty()) continue;
            if (requested.equalsIgnoreCase("instances") && instance) return true;
            if (requested.equalsIgnoreCase(worldName)) return true;
            if (!displayName.isEmpty() && requested.equalsIgnoreCase(displayName)) return true;
            if (!generatedName.isEmpty() && (requested.equalsIgnoreCase(generatedName)
                    || requested.equalsIgnoreCase("instance-" + generatedName))) return true;
        }
        return false;
    }

    private static String stableGeneratedInstanceName(String worldName) {
        String value = safe(worldName);
        if (!value.regionMatches(true, 0, "instance-", 0, 9) || value.length() <= 46) return "";
        int uuidStart = value.length() - 36;
        if (uuidStart <= 10 || value.charAt(uuidStart - 1) != '-') return "";
        try { UUID.fromString(value.substring(uuidStart)); }
        catch (IllegalArgumentException ex) { return ""; }
        return value.substring(9, uuidStart - 1);
    }

    private static String normalizeMode(String value) {
        String mode = safe(value).toLowerCase(Locale.ROOT);
        return mode.equals("once") || mode.equals("always") || mode.equals("dismissible") ? mode : "dismissible";
    }

    private static boolean booleanValue(Object value, boolean fallback) { return value instanceof Boolean b ? b : fallback; }
    private static int intValue(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static String stringValue(Object value, String fallback) { return value instanceof String s && !s.isBlank() ? s : fallback; }
    private static String safe(String value) { return value == null ? "" : value.strip(); }
}
