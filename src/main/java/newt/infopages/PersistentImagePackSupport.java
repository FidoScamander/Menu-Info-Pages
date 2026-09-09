package newt.infopages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class PersistentImagePackSupport {
    private PersistentImagePackSupport() {}

    static void ensureManifest(Path assetRoot) {
        if (assetRoot == null) return;
        Path manifest = assetRoot.resolve("manifest.json");
        String body = """
                {
                  "Group": "Newt",
                  "Name": "MenuInfoPagesRemote",
                  "Version": "1.3.0",
                  "Description": "MenuInfoPages cached runtime UI images",
                  "ServerVersion": ">=0.6.0 <0.7.0",
                  "IncludesAssetPack": true
                }
                """;
        try {
            Files.createDirectories(assetRoot);
            if (!Files.exists(manifest) || !Files.readString(manifest, StandardCharsets.UTF_8).equals(body)) {
                Files.writeString(manifest, body, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            System.err.println("[MenuInfoPages] Could not update runtime asset-pack manifest: " + ex.getMessage());
        }
    }
}
