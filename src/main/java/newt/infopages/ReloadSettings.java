package newt.infopages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ReloadSettings {
    private static final Path FILE = Path.of("mods", "MenuInfoPages", "reload.yml");
    private static final Object LOCK = new Object();
    private static volatile long cachedMtime = Long.MIN_VALUE;
    private static volatile boolean cachedReloadImages;
    private static volatile Boolean lastLoggedValue;

    private ReloadSettings() {}

    static void initialize() {
        ensureFile();
        reloadImagesEnabled();
    }

    static boolean reloadImagesEnabled() {
        synchronized (LOCK) {
            ensureFile();
            try {
                long mtime = Files.getLastModifiedTime(FILE).toMillis();
                if (mtime != cachedMtime) {
                    cachedReloadImages = readValue();
                    cachedMtime = mtime;
                    logMode(cachedReloadImages, false);
                }
            } catch (IOException ex) {
                System.err.println("[MenuInfoPages] Could not read reload.yml: " + ex.getMessage());
            }
            return cachedReloadImages;
        }
    }

    private static void ensureFile() {
        if (Files.exists(FILE)) return;
        try {
            Files.createDirectories(FILE.getParent());
            try (var in = ReloadSettings.class.getResourceAsStream("/defaults/reload.yml")) {
                String text = in == null ? "reload-images: false\n" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(FILE, text, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            System.err.println("[MenuInfoPages] Could not create reload.yml: " + ex.getMessage());
        }
    }

    private static boolean readValue() throws IOException {
        for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            if (trimmed.substring(0, colon).trim().equalsIgnoreCase("reload-images")) {
                return Boolean.parseBoolean(trimmed.substring(colon + 1).trim());
            }
        }
        return false;
    }

    private static void logMode(boolean enabled, boolean force) {
        if (!force && lastLoggedValue != null && lastLoggedValue == enabled) return;
        lastLoggedValue = enabled;
        if (enabled) {
            System.out.println("[MenuInfoPages] reload.yml: reload-images=true. Experimental live local/remote image reload enabled; connected players may temporarily see incorrect UI/HUD textures until reconnecting.");
        } else {
            System.out.println("[MenuInfoPages] reload.yml: reload-images=false. New/changed local and remote PNG images activate on server restart (recommended production mode).");
        }
    }
}
