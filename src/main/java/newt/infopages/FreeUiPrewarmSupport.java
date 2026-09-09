package newt.infopages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class FreeUiPrewarmSupport {
    private static final Path PAGES = Path.of("mods", "MenuInfoPages", "pages");
    private FreeUiPrewarmSupport() {}

    @SuppressWarnings("unchecked")
    static void warmup() {
        int texts = 0;
        int images = 0;
        if (!Files.isDirectory(PAGES)) return;
        try (var stream = Files.list(PAGES)) {
            for (Path path : stream.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                Object parsed = SimpleJson.parse(Files.readString(path, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> raw)) continue;
                PageDefinition page = PageDefinition.fromJson((Map<String, Object>) raw);
                RichTextCacheSupport.format(page.title); texts++;
                RichTextCacheSupport.format(page.intro); texts++;
                RichTextCacheSupport.format(page.footer); texts++;
                for (PageDefinition.Section section : page.sections) {
                    RichTextCacheSupport.format(section.title());
                    RichTextCacheSupport.format(section.text());
                    texts += 2;
                    if (!section.image().isBlank() && ImageSizeCacheSupport.readImageSize(ImageSourceSupport.resolveSource(section.image())) != null) images++;
                }
                for (String image : page.images) if (ImageSizeCacheSupport.readImageSize(ImageSourceSupport.resolveSource(image)) != null) images++;
            }
            if (texts + images > 0) System.out.println("[MenuInfoPages] UI cache prewarm: " + texts + " text fragments, " + images + " image sizes.");
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] UI prewarm skipped: " + ex.getMessage());
        }
    }
}
