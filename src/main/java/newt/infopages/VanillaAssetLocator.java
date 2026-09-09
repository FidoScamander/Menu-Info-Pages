package newt.infopages;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Locates the vanilla Hytale asset archive without assuming one installation layout. */
final class VanillaAssetLocator {
    private VanillaAssetLocator() {}

    static List<Path> locateAssetArchives() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        addRoots(candidates, Path.of("").toAbsolutePath().normalize(), 4);

        try {
            CodeSource source = MenuInfoPagesPlugin.class.getProtectionDomain().getCodeSource();
            if (source != null) {
                URI uri = source.getLocation().toURI();
                Path location = Path.of(uri).toAbsolutePath().normalize();
                addRoots(candidates, Files.isDirectory(location) ? location : location.getParent(), 5);
            }
        } catch (Exception ignored) {
            // The working-directory scan still covers normal dedicated-server layouts.
        }

        List<Path> archives = new ArrayList<>();
        for (Path root : candidates) {
            addIfFile(archives, root.resolve("Assets.zip"));
            addIfFile(archives, root.resolve("Server").resolve("Assets.zip"));
            addIfFile(archives, root.resolve("Hytale").resolve("Assets.zip"));
        }
        return List.copyOf(archives);
    }

    private static void addRoots(LinkedHashSet<Path> roots, Path start, int parentDepth) {
        Path current = start;
        for (int i = 0; current != null && i <= parentDepth; i++) {
            roots.add(current);
            current = current.getParent();
        }
    }

    private static void addIfFile(List<Path> result, Path path) {
        if (path != null && Files.isRegularFile(path)) result.add(path.toAbsolutePath().normalize());
    }
}
