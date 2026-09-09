package newt.infopages;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ReleaseDocsSupport {
    private static final Path DATA_DIR = Path.of("mods", "MenuInfoPages");
    private static final Path README = DATA_DIR.resolve("README.txt");
    private ReleaseDocsSupport() {}

    static void writeReadme() {
        try (InputStream in = ReleaseDocsSupport.class.getResourceAsStream("/README.md")) {
            if (in == null) return;
            Files.createDirectories(DATA_DIR);
            Files.writeString(README, new String(in.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] Could not write runtime README: " + ex.getMessage());
        }
    }
}
