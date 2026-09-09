package newt.infopages;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LocalImageSupport {
    private static final Path SOURCE_DIR = Path.of("mods", "MenuInfoPages", "images");
    private static final Path TARGET_DIR = Path.of("mods", "MenuInfoPages", "cache", "remote-assets", "Common", "UI", "Custom", "Pages", "Local");
    private static final String ASSET_PREFIX = "UI/Custom/Pages/Local/";
    private static final Object LOCK = new Object();
    private static volatile Map<String, Entry> byAlias = Map.of();

    private LocalImageSupport() {}

    static void refresh() { refresh(List.of()); }

    static void refresh(List<PageDefinition> pages) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(SOURCE_DIR);
                Files.createDirectories(TARGET_DIR);
                Set<String> requested = collectRequestedNames(pages);
                Map<String, Entry> next = new HashMap<>();
                Set<Path> keep = new HashSet<>();
                int synced = 0;
                int missing = 0;

                for (String name : requested) {
                    Path source = SOURCE_DIR.resolve(name).normalize();
                    if (!source.getParent().equals(SOURCE_DIR) || !Files.isRegularFile(source)) {
                        System.err.println("[MenuInfoPages] Local image source is missing: " + name + " (expected in mods/MenuInfoPages/images/).");
                        missing++;
                        continue;
                    }
                    ImageInfo info = inspectImage(source);
                    Path target = TARGET_DIR.resolve(name).normalize();
                    if (!target.getParent().equals(TARGET_DIR)) continue;
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target) || Files.mismatch(source, target) != -1) {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        synced++;
                    }
                    keep.add(target);
                    Entry entry = new Entry(target, ASSET_PREFIX + name, info.width, info.height);
                    putAliases(next, name, entry);
                }

                try (var stream = Files.list(TARGET_DIR)) {
                    stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                            .filter(path -> !keep.contains(path)).forEach(path -> {
                                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                            });
                }
                byAlias = Map.copyOf(next);
                if (!requested.isEmpty()) {
                    System.out.println("[MenuInfoPages] Local images: " + next.values().stream().distinct().count()
                            + " active/referenced, " + synced + " synced, " + missing + " missing source file(s).");
                }
            } catch (Exception ex) {
                System.err.println("[MenuInfoPages] Local image refresh failed: " + ex.getMessage());
            }
        }
    }

    static String resolve(String value) {
        if (value == null || value.isBlank()) return value;
        Entry entry = byAlias.get(normalizeAlias(value));
        return entry == null ? value : entry.assetPath;
    }

    static int[] readImageSize(String value) {
        Entry entry = byAlias.get(normalizeAlias(value));
        return entry == null ? null : new int[]{entry.width, entry.height};
    }

    private static Set<String> collectRequestedNames(List<PageDefinition> pages) {
        Set<String> names = new HashSet<>();
        for (PageDefinition page : pages) {
            for (String image : page.images) {
                String name = localFileName(image); if (name != null) names.add(name);
            }
            for (PageDefinition.Section section : page.sections) {
                String name = localFileName(section.image()); if (name != null) names.add(name);
            }
        }
        return names;
    }

    private static String localFileName(String value) {
        if (value == null || value.isBlank() || value.startsWith("http://") || value.startsWith("https://")) return null;
        String normalized = value.replace('\\', '/').strip();
        if (normalized.startsWith("images/")) normalized = normalized.substring(7);
        if (normalized.contains("/")) return null;
        return normalized.toLowerCase(Locale.ROOT).endsWith(".png") ? normalized : null;
    }

    private static String normalizeAlias(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').strip().toLowerCase(Locale.ROOT);
        return normalized.startsWith("images/") ? normalized.substring(7) : normalized;
    }

    private static void putAliases(Map<String, Entry> map, String name, Entry entry) {
        map.put(normalizeAlias(name), entry);
        map.put(normalizeAlias("images/" + name), entry);
        map.put(normalizeAlias(entry.assetPath), entry);
    }

    private static ImageInfo inspectImage(Path path) throws IOException {
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) throw new IOException("Only PNG images are supported: " + path.getFileName());
        try (var in = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("Invalid PNG: " + path.getFileName());
            return new ImageInfo(image.getWidth(), image.getHeight());
        }
    }

    private record Entry(Path file, String assetPath, int width, int height) {}
    private record ImageInfo(int width, int height) {}
}
