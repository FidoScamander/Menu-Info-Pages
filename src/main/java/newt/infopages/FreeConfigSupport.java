package newt.infopages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FreeConfigSupport {
    private static final Path CONFIG = Path.of("mods", "MenuInfoPages", "config.json");

    private FreeConfigSupport() {}

    static void ensure() {
        try {
            Files.createDirectories(CONFIG.getParent());
            if (!Files.exists(CONFIG)) {
                write(defaultRoot());
                return;
            }
            Object parsed = SimpleJson.parse(Files.readString(CONFIG, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> existing)) return;
            if (existing.containsKey("itemCommands")) return;

            LinkedHashMap<String, Object> migrated = new LinkedHashMap<>();
            for (var entry : existing.entrySet()) {
                if (entry.getKey() instanceof String key) migrated.put(key, entry.getValue());
            }
            migrated.put("itemCommands", demoItemCommands());
            write(migrated);
            System.out.println("[MenuInfoPages] Added missing itemCommands section to config.json.");
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] Could not initialize config.json: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readRoot() throws IOException {
        ensure();
        Object parsed = SimpleJson.parse(Files.readString(CONFIG, StandardCharsets.UTF_8));
        return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static LinkedHashMap<String, Object> defaultRoot() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("itemCommands", demoItemCommands());
        return root;
    }

    private static Map<String, Object> demoItemCommands() {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", "demo");
        item.put("sourceItem", "MenuInfoPages_Demo_Grimoire_Source");
        item.put("name", "Demo Pages");
        item.put("quality", "");
        item.put("leftClickCommand", "guide");
        item.put("rightClickCommand", "guide");

        LinkedHashMap<String, Object> section = new LinkedHashMap<>();
        section.put("enabled", true);
        section.put("items", new ArrayList<>(List.of(item)));
        return section;
    }

    private static void write(Map<String, Object> root) throws IOException {
        Files.writeString(CONFIG, JsonWriter.write(root) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
