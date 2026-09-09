package newt.infopages;

import com.hypixel.hytale.server.core.command.system.CommandRegistration;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small bootstrap used by the Free edition. The runtime services are kept in
 * separate classes so page parsing, image handling and auto-open state remain
 * independently testable.
 */
public class NewtInfoPagesPlugin extends JavaPlugin {
    private static final String PREFIX = "[MenuInfoPages] ";
    private static final Path DATA_DIR = Path.of("mods", "MenuInfoPages");
    private final Path pagesDirectory = DATA_DIR.resolve("pages");

    public NewtInfoPagesPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        try {
            ensureDefaultFiles();
            registerPages(loadPages());
        } catch (Exception ex) {
            System.err.println(PREFIX + "Startup failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    private void registerPages(List<PageDefinition> pages) {
        Set<String> commands = new HashSet<>();
        int registered = 0;
        for (PageDefinition page : pages) {
            if (!commands.add(page.command)) {
                System.err.println(PREFIX + "Duplicate page command ignored: /" + page.command);
                continue;
            }
            try {
                CommandRegistration registration = getCommandRegistry().registerCommand(new InfoPageCommand(page));
                AutoOpenManager.trackRegistration(page.command, registration);
                registered++;
                System.out.println(PREFIX + "Registered /" + page.command + " -> " + page.title);
            } catch (RuntimeException ex) {
                System.err.println(PREFIX + "Could not register /" + page.command + ": " + ex.getMessage());
            }
        }
        System.out.println(PREFIX + "Started with " + registered + " page(s).");
    }

    private List<PageDefinition> loadPages() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pagesDirectory, "*.json")) {
            for (Path file : stream) files.add(file);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));

        List<PageDefinition> pages = new ArrayList<>();
        for (Path file : files) {
            try {
                Object parsed = SimpleJson.parse(Files.readString(file, StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> raw)) {
                    throw new IllegalArgumentException("JSON root must be an object");
                }
                @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) raw;
                pages.add(PageDefinition.fromJson(root));
            } catch (Exception ex) {
                System.err.println(PREFIX + "Ignored " + file.getFileName() + ": " + ex.getMessage());
            }
        }
        return pages;
    }

    private void ensureDefaultFiles() throws IOException {
        Files.createDirectories(pagesDirectory);
        copyDefaultIfMissing("/defaults/example.json", pagesDirectory.resolve("example.json"));
        copyDefaultIfMissing("/defaults/README.txt", DATA_DIR.resolve("README.txt"));
    }

    private static void copyDefaultIfMissing(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        try (InputStream in = NewtInfoPagesPlugin.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing bundled resource " + resource);
            Files.write(target, in.readAllBytes());
        }
    }
}
