package newt.infopages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Access to the self-contained source item used by the fresh-install demo. */
final class BundledDemoSourceSupport {
    static final String SOURCE_ID = "MenuInfoPages_Demo_Grimoire_Source";
    private static final String RESOURCE = "/defaults/itemcommands/MenuInfoPages_Demo_Grimoire_Source.json";

    private BundledDemoSourceSupport() {}

    static ItemCommandSupport.SourceItem locate(String sourceId) throws IOException {
        if (!SOURCE_ID.equals(sourceId)) return null;
        try (InputStream input = BundledDemoSourceSupport.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Bundled demo source is missing: " + RESOURCE);
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String container = bundledContainerKey();
            return new ItemCommandSupport.SourceItem(json, container, "bundled demo source");
        }
    }

    private static String bundledContainerKey() {
        try {
            var source = BundledDemoSourceSupport.class.getProtectionDomain().getCodeSource();
            if (source != null) return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize().toString();
        } catch (Exception ignored) {
        }
        return "classpath";
    }
}
