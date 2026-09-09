package newt.infopages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandRegistration;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AutoOpenManager {
    static final Path PAGES_DIR = Path.of("mods", "MenuInfoPages", "pages");
    private static final Path STATE_FILE = Path.of("mods", "MenuInfoPages", "autoopen-state.json");
    private static final Object RELOAD_LOCK = new Object();
    private static final Map<UUID, Map<String, PlayerPageState>> STATES = new HashMap<>();
    private static final Map<UUID, String> LAST_READY_WORLD = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_CONFLICTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, CommandRegistration> PAGE_REGISTRATIONS = new HashMap<>();
    private static volatile Map<String, Entry> entries = Map.of();
    private static volatile CommandRegistry commandRegistry;

    private AutoOpenManager() {}

    static void initialize(CommandRegistry registry) {
        commandRegistry = registry;
        loadState();
        try {
            reloadFromDisk(false);
        } catch (IOException ex) {
            System.err.println("[MenuInfoPages] AutoOpen initialization failed: " + ex.getMessage());
        }
    }

    static ReloadResult reloadFromDisk(boolean refreshImages) throws IOException {
        synchronized (RELOAD_LOCK) {
            Files.createDirectories(PAGES_DIR);
            ParsedDefinitions parsed = parseDefinitions();
            applyDefinitions(parsed, refreshImages);
            int cleaned = cleanupObsoleteState();
            RichTextCacheSupport.clear();
            ImageSizeCacheSupport.clear();
            return new ReloadResult(parsed.entries.size(), parsed.updatedCommands, parsed.newCommands, cleaned);
        }
    }

    static void trackRegistration(String command, CommandRegistration registration) {
        if (command == null || registration == null) return;
        synchronized (RELOAD_LOCK) {
            PAGE_REGISTRATIONS.put(PageDefinition.normalizeCommand(command), registration);
        }
    }

    private static ParsedDefinitions parseDefinitions() throws IOException {
        List<Path> files;
        try (var stream = Files.list(PAGES_DIR)) {
            files = stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        }

        Map<String, Entry> next = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int autoOpenCount = 0;
        for (Path file : files) {
            try {
                Object parsed = SimpleJson.parse(Files.readString(file, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("root must be a JSON object");
                @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) raw;
                PageDefinition page = PageDefinition.fromJson(root);
                AutoOpenConfig autoOpen = AutoOpenConfig.fromPageMap(root);
                if (next.putIfAbsent(page.command, new Entry(file, page, autoOpen)) != null) {
                    throw new IllegalArgumentException("duplicate command /" + page.command);
                }
                if (autoOpen.enabled) autoOpenCount++;
            } catch (Exception ex) {
                errors.add(file.getFileName() + ": " + ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new IOException("Invalid page configuration; active definitions were kept. " + String.join(" | ", errors));
        }
        return new ParsedDefinitions(next, autoOpenCount);
    }

    private static void applyDefinitions(ParsedDefinitions parsed, boolean refreshImages) {
        Map<String, Entry> old = entries;
        Set<String> oldCommands = new HashSet<>(old.keySet());
        Set<String> nextCommands = parsed.entries.keySet();
        int updated = 0;
        int added = 0;

        if (commandRegistry != null) {
            for (String command : oldCommands) {
                if (nextCommands.contains(command)) continue;
                CommandRegistration registration = PAGE_REGISTRATIONS.remove(command);
                if (registration != null) {
                    try { registration.unregister(); }
                    catch (RuntimeException ex) { System.err.println("[MenuInfoPages] Could not unregister /" + command + ": " + ex.getMessage()); }
                }
            }

            for (Entry entry : parsed.entries.values()) {
                CommandRegistration previous = PAGE_REGISTRATIONS.remove(entry.page.command);
                if (previous != null) {
                    try { previous.unregister(); } catch (RuntimeException ignored) {}
                    updated++;
                } else {
                    added++;
                }
                try {
                    CommandRegistration registration = commandRegistry.registerCommand(new InfoPageCommand(entry.page));
                    PAGE_REGISTRATIONS.put(entry.page.command, registration);
                } catch (RuntimeException ex) {
                    System.err.println("[MenuInfoPages] Could not register /" + entry.page.command + ": " + ex.getMessage());
                }
            }
        }

        parsed.updatedCommands = updated;
        parsed.newCommands = added;
        entries = Map.copyOf(parsed.entries);
        PagePermissionSupport.reload(PAGES_DIR);
        Collection<PageDefinition> pages = parsed.entries.values().stream().map(Entry::page).toList();
        LocalImageSupport.refresh(new ArrayList<>(pages));
        if (refreshImages && ReloadSettings.reloadImagesEnabled()) ImageSourceSupport.refreshPages(new ArrayList<>(pages));
        LOGGED_CONFLICTS.clear();
        if (parsed.autoOpenCount > 0) System.out.println("[MenuInfoPages] Auto-open enabled on " + parsed.autoOpenCount + " page(s).");
    }

    static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        PlayerRef ref = player.getPlayerRef();
        World world = player.getWorld();
        if (ref == null || world == null) return;

        String worldId = worldIdentity(world);
        String previous = LAST_READY_WORLD.put(ref.getUuid(), worldId);
        if (worldId.equals(previous)) return;

        List<Entry> candidates = entries.values().stream()
                .filter(entry -> entry.autoOpen.enabled && entry.autoOpen.matches(world))
                .filter(entry -> !entry.autoOpen.isOnce() || !isSeen(ref.getUuid(), entry.page.command, entry.autoOpen.version))
                .filter(entry -> !entry.autoOpen.isDismissible() || !isDismissed(ref.getUuid(), entry.page.command, entry.autoOpen.version))
                .toList();

        if (candidates.size() != 1) {
            if (candidates.size() > 1) {
                String key = worldId + ":" + candidates.stream().map(entry -> entry.page.command).sorted().toList();
                if (LOGGED_CONFLICTS.add(key)) {
                    System.err.println("[MenuInfoPages] AutoOpen conflict in " + worldId + ": "
                            + candidates.stream().map(entry -> "/" + entry.page.command).sorted().toList());
                }
            }
            return;
        }

        Entry entry = candidates.getFirst();
        if (!PagePermissionSupport.autoOpenAllowed(false, ref, entry.page.command)
                && !PagePermissionSupport.autoOpenAllowed(false, player, entry.page.command)) return;

        Runnable open = () -> openIfStillEligible(ref, worldId, entry.page.command);
        if (entry.autoOpen.delayMs <= 0) {
            world.execute(open);
        } else {
            Thread.startVirtualThread(() -> {
                try { Thread.sleep(entry.autoOpen.delayMs); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                runOnCurrentWorldThread(ref, open);
            });
        }
    }

    static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (event.getPlayerRef() != null) LAST_READY_WORLD.remove(event.getPlayerRef().getUuid());
    }

    static boolean isDismissible(String command) {
        Entry entry = entries.get(normalizeCommandInput(command));
        return entry != null && entry.autoOpen.isDismissible();
    }

    static boolean isDismissed(PlayerRef player, String command) {
        Entry entry = entries.get(normalizeCommandInput(command));
        return entry != null && isDismissed(player.getUuid(), entry.page.command, entry.autoOpen.version);
    }

    static synchronized boolean toggleDismissed(PlayerRef player, String command) {
        Entry entry = entries.get(normalizeCommandInput(command));
        if (entry == null || !entry.autoOpen.isDismissible()) return false;
        PlayerPageState state = state(player.getUuid(), entry.page.command, true);
        state.version = entry.autoOpen.version;
        state.dismissed = !state.dismissed;
        saveState();
        return state.dismissed;
    }

    private static void openIfStillEligible(PlayerRef playerRef, String expectedWorld, String command) {
        Entry entry = entries.get(command);
        if (entry == null) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null || !(store.getExternalData() instanceof EntityStore entityStore)) return;
        World world = entityStore.getWorld();
        if (world == null || !worldIdentity(world).equals(expectedWorld) || !entry.autoOpen.matches(world)) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || (!PagePermissionSupport.autoOpenAllowed(false, player, command)
                && !PagePermissionSupport.autoOpenAllowed(false, playerRef, command))) return;
        if (entry.autoOpen.isOnce() && isSeen(playerRef.getUuid(), command, entry.autoOpen.version)) return;
        if (entry.autoOpen.isDismissible() && isDismissed(playerRef.getUuid(), command, entry.autoOpen.version)) return;

        player.getPageManager().openCustomPage(ref, store, new InfoPageUI(playerRef, entry.page));
        if (entry.autoOpen.isOnce()) markSeen(playerRef.getUuid(), command, entry.autoOpen.version);
    }

    private static void runOnCurrentWorldThread(PlayerRef player, Runnable task) {
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            Object external = store.getExternalData();
            if (external instanceof EntityStore entityStore && entityStore.getWorld() != null) entityStore.getWorld().execute(task);
        } catch (Throwable ignored) {
        }
    }

    private static String worldIdentity(World world) {
        try {
            if (world.getWorldConfig() != null && world.getWorldConfig().getUuid() != null) return world.getWorldConfig().getUuid().toString();
        } catch (Throwable ignored) {}
        return world.getName() == null ? "" : world.getName();
    }

    private static synchronized boolean isSeen(UUID player, String command, int version) {
        PlayerPageState state = state(player, command, false);
        return state != null && state.version >= version && state.seen;
    }

    private static synchronized boolean isDismissed(UUID player, String command, int version) {
        PlayerPageState state = state(player, command, false);
        return state != null && state.version >= version && state.dismissed;
    }

    private static synchronized void markSeen(UUID player, String command, int version) {
        PlayerPageState state = state(player, command, true);
        state.version = version;
        state.seen = true;
        saveState();
    }

    private static PlayerPageState state(UUID player, String command, boolean create) {
        Map<String, PlayerPageState> perPlayer = STATES.get(player);
        if (perPlayer == null && create) {
            perPlayer = new HashMap<>();
            STATES.put(player, perPlayer);
        }
        if (perPlayer == null) return null;
        PlayerPageState state = perPlayer.get(command);
        if (state == null && create) {
            state = new PlayerPageState();
            perPlayer.put(command, state);
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void loadState() {
        STATES.clear();
        if (!Files.isRegularFile(STATE_FILE)) return;
        try {
            Object parsed = SimpleJson.parse(Files.readString(STATE_FILE, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> root)) return;
            for (var playerEntry : root.entrySet()) {
                if (!(playerEntry.getKey() instanceof String uuidText) || !(playerEntry.getValue() instanceof Map<?, ?> pages)) continue;
                UUID uuid;
                try { uuid = UUID.fromString(uuidText); } catch (IllegalArgumentException ex) { continue; }
                Map<String, PlayerPageState> perPlayer = new HashMap<>();
                for (var pageEntry : pages.entrySet()) {
                    if (!(pageEntry.getKey() instanceof String command) || !(pageEntry.getValue() instanceof Map<?, ?> values)) continue;
                    PlayerPageState state = new PlayerPageState();
                    if (values.get("version") instanceof Number n) state.version = n.intValue();
                    state.seen = Boolean.TRUE.equals(values.get("seen"));
                    state.dismissed = Boolean.TRUE.equals(values.get("dismissed"));
                    perPlayer.put(command, state);
                }
                if (!perPlayer.isEmpty()) STATES.put(uuid, perPlayer);
            }
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] Could not read autoopen-state.json: " + ex.getMessage());
        }
    }

    private static synchronized void saveState() {
        try {
            Files.createDirectories(STATE_FILE.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            for (var player : STATES.entrySet()) {
                Map<String, Object> pages = new LinkedHashMap<>();
                for (var page : player.getValue().entrySet()) {
                    PlayerPageState state = page.getValue();
                    pages.put(page.getKey(), Map.of("version", state.version, "seen", state.seen, "dismissed", state.dismissed));
                }
                if (!pages.isEmpty()) root.put(player.getKey().toString(), pages);
            }
            Files.writeString(STATE_FILE, JsonWriter.write(root) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            System.err.println("[MenuInfoPages] Could not save auto-open state: " + ex.getMessage());
        }
    }

    private static synchronized int cleanupObsoleteState() {
        int before = STATES.values().stream().mapToInt(Map::size).sum();
        Set<String> valid = entries.keySet();
        STATES.values().forEach(map -> map.keySet().removeIf(command -> !valid.contains(command)));
        STATES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        int after = STATES.values().stream().mapToInt(Map::size).sum();
        if (after != before) saveState();
        return before - after;
    }

    static Path createNewPage(String requestedName, String requestedCommand) throws IOException {
        String fileName = requestedName == null ? "" : requestedName.strip();
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Invalid page filename: " + requestedName);
        }
        if (!fileName.toLowerCase().endsWith(".json")) fileName += ".json";
        String command = normalizeCommandInput(requestedCommand);
        if (entries.containsKey(command)) throw new IllegalArgumentException("Command already exists: /" + command);
        Files.createDirectories(PAGES_DIR);
        Path target = PAGES_DIR.resolve(fileName).normalize();
        if (!target.getParent().equals(PAGES_DIR) || Files.exists(target)) throw new IllegalArgumentException("Page file already exists: " + fileName);
        Files.writeString(target, newPageTemplate(fileName, command), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return target;
    }

    private static String newPageTemplate(String fileName, String command) {
        String title = fileName.replaceFirst("(?i)\\.json$", "").replace('_', ' ').replace('-', ' ').strip();
        if (title.isEmpty()) title = command;
        return """
                {
                  "autoOpen": {"enabled": false, "mode": "dismissible", "version": 1, "worlds": [], "delayMs": 1500},
                  "command": "%s",
                  "permission": "",
                  "description": "Open %s",
                  "title": "%s",
                  "intro": "",
                  "images": [],
                  "sections": [],
                  "buttons": [{"text": "CLOSE", "command": "", "runAs": "player", "close": true, "style": "danger"}],
                  "footer": "Press Esc to close."
                }
                """.formatted(JsonWriter.escape(command), JsonWriter.escape(title), JsonWriter.escape(title));
    }

    static String normalizeCommandInput(String command) { return PageDefinition.normalizeCommand(command); }

    record ReloadResult(int pages, int updatedCommands, int newCommands, int cleanedState) {
        String summary() {
            return pages + " page(s), " + updatedCommands + " updated, " + newCommands + " new commands, " + cleanedState + " obsolete state entries removed";
        }
    }

    private record Entry(Path file, PageDefinition page, AutoOpenConfig autoOpen) {}
    private static final class PlayerPageState { int version; boolean seen; boolean dismissed; }
    private static final class ParsedDefinitions {
        final Map<String, Entry> entries;
        final int autoOpenCount;
        int updatedCommands;
        int newCommands;
        ParsedDefinitions(Map<String, Entry> entries, int autoOpenCount) { this.entries = entries; this.autoOpenCount = autoOpenCount; }
    }
}
