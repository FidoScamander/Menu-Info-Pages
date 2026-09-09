package newt.infopages;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates the small runtime asset pack used by ItemCommands.
 *
 * <p>The implementation intentionally keeps Hytale API touch-points behind reflection here. Asset
 * discovery and cloning is ordinary file/ZIP work, which makes this part easy to audit and keeps
 * it tolerant of minor 0.6.x registry changes.</p>
 */
final class ItemCommandSupport {
    private static final Path CONFIG = Path.of("mods", "MenuInfoPages", "config.json");
    private static final Path ASSET_ROOT = Path.of("mods", "MenuInfoPages", "cache", "remote-assets");
    private static final Path ITEMS_DIR = ASSET_ROOT.resolve(Path.of("Server", "Item", "Items", "MenuInfoPages"));
    private static final Path ITEM_MANIFEST = ASSET_ROOT.resolve("itemcommands-managed-files.txt");
    private static final Map<String, Action> ACTIONS = new ConcurrentHashMap<>();

    private static volatile boolean initialized;
    private static volatile boolean interactionRegistered;
    private static volatile boolean assetsPrepared;

    private ItemCommandSupport() {}

    static synchronized void prepareAssetsBeforePackRegistration() {
        if (assetsPrepared) return;
        try {
            Object plugin = findImageSourcePlugin();
            if (plugin != null && !interactionRegistered) registerInteraction(plugin);
            loadConfigAndGenerateAssets();
            assetsPrepared = true;
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] ItemCommands pre-registration failed: " + message(ex));
        }
    }

    static synchronized void initializeFromBridge() {
        if (initialized) return;
        try {
            Object plugin = findPlugin();
            if (plugin != null && !interactionRegistered) registerInteraction(plugin);
            if (!assetsPrepared) {
                loadConfigAndGenerateAssets();
                assetsPrepared = true;
            }
            initialized = true;
            if (!ACTIONS.isEmpty()) {
                System.out.println("[MenuInfoPages] ItemCommands active: " + ACTIONS.size()
                        + " generated item(s). Restart required after structural item changes.");
            }
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] ItemCommands initialization failed: " + message(ex));
        }
    }

    private static Object findPlugin() {
        return FullFeatureBridge.plugin;
    }

    private static Object findImageSourcePlugin() {
        try {
            Field field = ImageSourceSupport.class.getDeclaredField("pluginInstance");
            field.setAccessible(true);
            Object value = field.get(null);
            return value != null ? value : FullFeatureBridge.plugin;
        } catch (ReflectiveOperationException ignored) {
            return FullFeatureBridge.plugin;
        }
    }

    private static void registerInteraction(Object plugin) throws Exception {
        if (plugin == null || interactionRegistered) return;

        Class<?> interaction = Class.forName(
                "com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction");
        Field codecField = interaction.getField("CODEC");
        Object codec = codecField.get(null);

        Method getCodecRegistry = null;
        for (Method method : plugin.getClass().getMethods()) {
            if (!method.getName().equals("getCodecRegistry") || method.getParameterCount() != 1) continue;
            getCodecRegistry = method;
            break;
        }
        if (getCodecRegistry == null) throw new NoSuchMethodException("getCodecRegistry(codec)");

        Object registry = getCodecRegistry.invoke(plugin, codec);
        Method register = null;
        for (Method method : registry.getClass().getMethods()) {
            if (method.getName().equals("register") && method.getParameterCount() == 3) {
                register = method;
                break;
            }
        }
        if (register == null) throw new NoSuchMethodException("codec registry register(name,type,codec)");
        register.invoke(registry, ItemCommandInteraction.TYPE_NAME,
                ItemCommandInteraction.class, ItemCommandInteraction.CODEC);
        interactionRegistered = true;
        System.out.println("[MenuInfoPages] Registered interaction: " + ItemCommandInteraction.TYPE_NAME + ".");
    }

    @SuppressWarnings("unchecked")
    private static void loadConfigAndGenerateAssets() throws Exception {
        ACTIONS.clear();
        clearManagedItemAssets();

        Map<String, Object> root = FreeConfigSupport.readRoot();
        Object sectionValue = root.get("itemCommands");
        if (!(sectionValue instanceof Map<?, ?> rawSection)) return;
        Map<String, Object> section = (Map<String, Object>) rawSection;
        if (!bool(section.get("enabled"), true)) return;
        if (!(section.get("items") instanceof Collection<?> items)) return;

        Files.createDirectories(ITEMS_DIR);
        int generated = 0;
        for (Object value : items) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            raw.forEach((key, val) -> {
                if (key instanceof String stringKey) item.put(stringKey, val);
            });

            String sourceId = str(item.get("sourceItem"));
            if (sourceId.isBlank()) continue;
            String configuredId = str(item.get("id"));
            String id = sanitizeId(configuredId.isBlank() ? sourceId : configuredId);
            if (id.isBlank()) continue;
            String actionId = id.toLowerCase(Locale.ROOT);
            String generatedAssetId = "MenuInfoPages_" + toAssetKeySegment(id);

            String left = normalizeCommand(str(item.get("leftClickCommand")));
            String right = normalizeCommand(str(item.get("rightClickCommand")));
            if (left.isBlank() && right.isBlank()) continue;

            SourceItem source = locateSourceItem(sourceId, generatedAssetId);
            if (source == null) {
                System.err.println("[MenuInfoPages] ItemCommands source item not found: " + sourceId);
                continue;
            }

            Map<String, Object> clone = flattenItem(sourceId, source, new LinkedHashSet<>());
            sanitizeClone(clone);

            String quality = str(item.get("quality"));
            if (!quality.isBlank()) clone.put("Quality", quality);

            String displayName = str(item.get("name"));
            if (!displayName.isBlank()) {
                LinkedHashMap<String, Object> translation = new LinkedHashMap<>();
                translation.put("Name", "menuinfopages.items." + generatedAssetId + ".name");
                clone.put("TranslationProperties", translation);
                writeNameTranslations(generatedAssetId, displayName);
            }

            clone.put("Interactions", interactionMap(actionId, !left.isBlank(), !right.isBlank()));
            copyCloneDependencies(source, clone);
            copyTranslationNamespaces(source, clone, generatedAssetId);

            Path output = ITEMS_DIR.resolve(generatedAssetId + ".json");
            Files.writeString(output, JsonWriter.write(clone) + System.lineSeparator(), StandardCharsets.UTF_8);
            ACTIONS.put(actionId, new Action(left, right));
            generated++;

            System.out.println("[MenuInfoPages] ItemCommands: copied " + sourceId + " from "
                    + source.description() + " -> " + generatedAssetId
                    + " (left=" + printable(left) + ", right=" + printable(right) + ").");
        }

        writeManagedItemManifest();
        if (generated == 0) pruneEmptyDirectories(ITEMS_DIR);
    }

    private static void copyCloneDependencies(SourceItem source, Map<String, Object> clone) {
        try {
            String quality = str(clone.get("Quality"));
            if (!quality.isBlank()) {
                String relative = "Server/Item/Qualities/" + quality + ".json";
                AssetBlob blob = locateAssetBlob(relative, source.containerKey());
                if (blob != null) {
                    Path target = ASSET_ROOT.resolve(relative);
                    writeAsset(target, blob.bytes());
                    System.out.println("[MenuInfoPages] ItemCommands: copied quality dependency " + quality
                            + " from " + blob.description() + ".");
                }
            }

            Set<String> commonRefs = new LinkedHashSet<>();
            collectCommonRefs(clone, commonRefs);
            int copied = 0;
            for (String ref : commonRefs) {
                copied += copyCommonResourceVariants(ref, source.containerKey());
            }
            if (copied > 0) {
                System.out.println("[MenuInfoPages] ItemCommands: copied " + copied
                        + " Common resource dependency file(s) for " + source.description() + ".");
            }
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] ItemCommands dependency copy warning: " + message(ex));
        }
    }

    private static void collectCommonRefs(Object value, Set<String> output) {
        if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) collectCommonRefs(child, output);
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectCommonRefs(child, output));
            return;
        }
        if (!(value instanceof String string)) return;
        String path = string.replace('\\', '/').trim();
        if (path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) return;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".blockymodel") || lower.endsWith(".ogg") || lower.endsWith(".wav")) {
            output.add(path.startsWith("Common/") ? path.substring("Common/".length()) : path);
        }
    }

    private static int copyCommonResourceVariants(String commonPath, String preferredContainer) throws IOException {
        String normalized = commonPath.replace('\\', '/');
        LinkedHashMap<String, AssetBlob> variants = locateAssetBlobVariants("Common/" + normalized, preferredContainer);
        int copied = 0;
        for (Map.Entry<String, AssetBlob> entry : variants.entrySet()) {
            Path target = ASSET_ROOT.resolve(entry.getKey());
            writeAsset(target, entry.getValue().bytes());
            copied++;
        }
        return copied;
    }

    private static LinkedHashMap<String, AssetBlob> locateAssetBlobVariants(
            String relativePath, String preferredContainer) throws IOException {
        LinkedHashMap<String, AssetBlob> result = new LinkedHashMap<>();
        AssetBlob exact = locateAssetBlob(relativePath, preferredContainer);
        if (exact != null) result.put(relativePath, exact);

        int dot = relativePath.lastIndexOf('.');
        if (dot > 0) {
            String stem = relativePath.substring(0, dot);
            String ext = relativePath.substring(dot);
            for (String suffix : List.of("@2x", "@3x")) {
                String variant = stem + suffix + ext;
                AssetBlob blob = locateAssetBlob(variant, preferredContainer);
                if (blob != null) result.putIfAbsent(variant, blob);
            }
        }
        return result;
    }

    private static AssetBlob locateAssetBlob(String relativePath, String preferredContainer) throws IOException {
        for (Path container : sourceContainers()) {
            if (preferredContainer != null && !preferredContainer.equals(containerKey(container))) continue;
            AssetBlob blob = readAssetFromContainer(container, relativePath);
            if (blob != null) return blob;
        }
        for (Path container : sourceContainers()) {
            AssetBlob blob = readAssetFromContainer(container, relativePath);
            if (blob != null) return blob;
        }
        try (InputStream input = ItemCommandSupport.class.getResourceAsStream("/" + relativePath)) {
            if (input != null) return new AssetBlob(input.readAllBytes(), "bundled resources");
        }
        return null;
    }

    private static AssetBlob readAssetFromContainer(Path container, String relativePath) throws IOException {
        if (container == null || !Files.isRegularFile(container)) return null;
        String normalized = relativePath.replace('\\', '/');
        String lower = container.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".jar") || lower.endsWith(".zip"))) return null;
        try (ZipFile zip = new ZipFile(container.toFile())) {
            ZipEntry entry = zip.getEntry(normalized);
            if (entry == null || entry.isDirectory()) return null;
            try (InputStream input = zip.getInputStream(entry)) {
                return new AssetBlob(input.readAllBytes(), container.getFileName() + ":" + normalized);
            }
        }
    }

    private static void copyTranslationNamespaces(SourceItem source, Map<String, Object> clone, String generatedAssetId) {
        Set<String> namespaces = new LinkedHashSet<>();
        addTranslationNamespace(clone.get("TranslationProperties"), namespaces);
        if (namespaces.isEmpty()) return;
        for (String namespace : namespaces) {
            try {
                copyNamespaceLangFiles(namespace, source.containerKey());
            } catch (IOException ex) {
                System.err.println("[MenuInfoPages] ItemCommands translation dependency warning for "
                        + generatedAssetId + ": " + message(ex));
            }
        }
    }

    private static void addTranslationNamespace(Object value, Set<String> output) {
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> addTranslationNamespace(child, output));
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> addTranslationNamespace(child, output));
        } else if (value instanceof String key) {
            int dot = key.indexOf('.');
            if (dot > 0) output.add(key.substring(0, dot));
        }
    }

    private static void copyNamespaceLangFiles(String namespace, String preferredContainer) throws IOException {
        for (String language : languages()) {
            String relative = "Server/Languages/" + language + "/" + namespace + ".lang";
            AssetBlob blob = locateAssetBlob(relative, preferredContainer);
            if (blob == null) continue;
            writeAsset(ASSET_ROOT.resolve(relative), blob.bytes());
        }
    }

    private static void clearManagedItemAssets() {
        try {
            if (Files.isRegularFile(ITEM_MANIFEST)) {
                for (String line : Files.readAllLines(ITEM_MANIFEST, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    Path candidate = ASSET_ROOT.resolve(trimmed).normalize();
                    if (candidate.startsWith(ASSET_ROOT)) Files.deleteIfExists(candidate);
                }
            }
            clearGeneratedItems();
            legacyClearManagedZones();
        } catch (IOException ex) {
            System.err.println("[MenuInfoPages] Could not clean old ItemCommands assets: " + message(ex));
        }
    }

    private static void legacyClearManagedZones() throws IOException {
        if (!Files.isDirectory(ASSET_ROOT)) return;
        // Old pre-manifest builds generated only these narrowly scoped areas. Do not touch page-image caches.
        for (Path zone : List.of(
                ASSET_ROOT.resolve(Path.of("Server", "Item", "Items", "MenuInfoPages")),
                ASSET_ROOT.resolve(Path.of("Server", "Item", "Qualities", "MenuInfoPages")))) {
            if (isItemManagedZone(ASSET_ROOT.relativize(zone).toString())) deleteTree(zone);
        }
    }

    private static boolean isItemManagedZone(String relative) {
        String value = relative.replace('\\', '/');
        return value.startsWith("Server/Item/Items/MenuInfoPages")
                || value.startsWith("Server/Item/Qualities/MenuInfoPages");
    }

    private static void writeManagedItemManifest() {
        try {
            Files.createDirectories(ITEM_MANIFEST.getParent());
            List<String> lines = new ArrayList<>();
            if (Files.isDirectory(ASSET_ROOT)) {
                try (var stream = Files.walk(ASSET_ROOT)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> !path.equals(ITEM_MANIFEST))
                            .filter(path -> isGeneratedItemAsset(path) || isGeneratedNameLang(path))
                            .sorted()
                            .forEach(path -> lines.add(ASSET_ROOT.relativize(path).toString().replace('\\', '/')));
                }
            }
            Files.write(ITEM_MANIFEST, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[MenuInfoPages] Could not write ItemCommands asset manifest: " + message(ex));
        }
    }

    private static boolean isGeneratedItemAsset(Path path) {
        String relative = ASSET_ROOT.relativize(path).toString().replace('\\', '/');
        return relative.startsWith("Server/Item/Items/MenuInfoPages/")
                || relative.startsWith("Server/Item/Qualities/MenuInfoPages_")
                || relative.startsWith("Common/Items/MenuInfoPages/")
                || relative.startsWith("Common/Icons/ItemsGenerated/MenuInfoPages_");
    }

    private static boolean isGeneratedNameLang(Path path) {
        String relative = ASSET_ROOT.relativize(path).toString().replace('\\', '/');
        return relative.startsWith("Server/Languages/") && relative.endsWith("/menuinfopages.lang");
    }

    private static void pruneEmptyDirectories(Path start) {
        if (start == null || !Files.isDirectory(start)) return;
        try (var walk = Files.walk(start)) {
            walk.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try (var children = Files.list(path)) {
                            if (children.findAny().isEmpty()) Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void clearGeneratedItems() throws IOException {
        deleteTree(ITEMS_DIR);
    }

    private static Map<String, Object> interactionMap(String actionId, boolean left, boolean right) {
        LinkedHashMap<String, Object> interactions = new LinkedHashMap<>();
        if (left) interactions.put("Primary", interactionRoot(actionId));
        if (right) interactions.put("Secondary", interactionRoot(actionId));
        return interactions;
    }

    private static Map<String, Object> interactionRoot(String actionId) {
        LinkedHashMap<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("Type", ItemCommandInteraction.TYPE_NAME);
        interaction.put("ActionId", actionId);
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("Interactions", new ArrayList<>(List.of(interaction)));
        return root;
    }

    private static void sanitizeClone(Map<String, Object> clone) {
        for (String key : List.of(
                "Parent", "Interactions", "InteractionVars", "PlayerAnimationsId",
                "Weapon", "Reticle", "ReticleId")) {
            clone.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flattenItem(String itemId, SourceItem source, Set<String> visited) throws Exception {
        String visitKey = source.containerKey() + ":" + itemId;
        if (!visited.add(visitKey)) throw new IOException("Item inheritance cycle at " + itemId);
        try {
            Object parsed = SimpleJson.parse(source.json());
            if (!(parsed instanceof Map<?, ?> raw)) throw new IOException("Item JSON is not an object: " + itemId);
            Map<String, Object> own = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key instanceof String stringKey) own.put(stringKey, deepCopy(value));
            });

            String parentId = str(own.get("Parent"));
            if (parentId.isBlank()) return own;
            SourceItem parent = locateSourceItem(parentId, parentId);
            if (parent == null) return own;
            Map<String, Object> merged = flattenItem(parentId, parent, visited);
            deepMerge(merged, own);
            return merged;
        } finally {
            visited.remove(visitKey);
        }
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, deepCopy(value)));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                if (key instanceof String stringKey) copy.put(stringKey, deepCopy(child));
            });
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(child -> copy.add(deepCopy(child)));
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> base, Map<String, Object> override) {
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object old = base.get(entry.getKey());
            Object value = entry.getValue();
            if (old instanceof Map<?, ?> oldMap && value instanceof Map<?, ?> newMap) {
                Map<String, Object> merged = new LinkedHashMap<>();
                ((Map<?, ?>) oldMap).forEach((key, child) -> {
                    if (key instanceof String s) merged.put(s, deepCopy(child));
                });
                ((Map<?, ?>) newMap).forEach((key, child) -> {
                    if (key instanceof String s) {
                        Object previous = merged.get(s);
                        if (previous instanceof Map<?, ?> previousMap && child instanceof Map<?, ?> childMap) {
                            Map<String, Object> nested = new LinkedHashMap<>();
                            ((Map<?, ?>) previousMap).forEach((k, v) -> { if (k instanceof String ks) nested.put(ks, deepCopy(v)); });
                            Map<String, Object> next = new LinkedHashMap<>();
                            ((Map<?, ?>) childMap).forEach((k, v) -> { if (k instanceof String ks) next.put(ks, deepCopy(v)); });
                            deepMerge(nested, next);
                            merged.put(s, nested);
                        } else {
                            merged.put(s, deepCopy(child));
                        }
                    }
                });
                base.put(entry.getKey(), merged);
            } else {
                base.put(entry.getKey(), deepCopy(value));
            }
        }
    }

    private static SourceItem locateSourceItem(String sourceId, String generatedId) throws IOException {
        SourceItem bundled = BundledDemoSourceSupport.locate(sourceId);
        if (bundled != null) return bundled;
        for (Path container : sourceContainers()) {
            SourceItem item = readItemFromContainer(container, sourceId);
            if (item != null) return item;
        }
        return null;
    }

    private static List<Path> sourceContainers() throws IOException {
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        try {
            CodeSource codeSource = ItemCommandSupport.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path own = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(own)) result.add(own);
            }
        } catch (Exception ignored) {
        }

        Path mods = Path.of("mods");
        if (Files.isDirectory(mods)) {
            try (var stream = Files.list(mods)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".jar") || name.endsWith(".zip");
                        })
                        .sorted()
                        .forEach(result::add);
            }
        }
        result.addAll(VanillaAssetLocator.locateAssetArchives());
        return List.copyOf(result);
    }

    private static String containerKey(Path container) {
        return container == null ? "" : container.toAbsolutePath().normalize().toString();
    }

    private static SourceItem readItemFromContainer(Path container, String itemId) throws IOException {
        if (container == null || !Files.isRegularFile(container)) return null;
        String prefix = "Server/Item/Items/";
        String exact = prefix + itemId + ".json";
        try (ZipFile zip = new ZipFile(container.toFile())) {
            ZipEntry direct = zip.getEntry(exact);
            if (direct != null) {
                try (InputStream input = zip.getInputStream(direct)) {
                    return new SourceItem(new String(input.readAllBytes(), StandardCharsets.UTF_8),
                            containerKey(container), container.getFileName() + ":" + direct.getName());
                }
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix) || !entry.getName().endsWith(".json")) continue;
                String file = entry.getName().substring(entry.getName().lastIndexOf('/') + 1,
                        entry.getName().length() - ".json".length());
                if (!file.equals(itemId)) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    return new SourceItem(new String(input.readAllBytes(), StandardCharsets.UTF_8),
                            containerKey(container), container.getFileName() + ":" + entry.getName());
                }
            }
        }
        return null;
    }

    private static void writeNameTranslations(String generatedAssetId, String displayName) throws Exception {
        for (String language : languages()) {
            Path file = ASSET_ROOT.resolve(Path.of("Server", "Languages", language, "menuinfopages.lang"));
            Files.createDirectories(file.getParent());
            String key = "items." + generatedAssetId + ".name";
            LinkedHashMap<String, String> entries = new LinkedHashMap<>();
            if (Files.isRegularFile(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int equals = line.indexOf('=');
                    if (equals > 0) entries.put(line.substring(0, equals).trim(), line.substring(equals + 1));
                }
            }
            entries.put(key, displayName);
            StringBuilder out = new StringBuilder();
            entries.forEach((k, v) -> out.append(k).append('=').append(v).append(System.lineSeparator()));
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> languages() {
        return List.of("ar-SA", "da-DK", "de-DE", "en-US", "es-ES", "fi-FI", "fr-FR", "hu-HU",
                "id-ID", "it-IT", "ja-JP", "ko-KR", "nl-NL", "no-NO", "pl-PL", "pt-BR", "pt-PT",
                "ro-RO", "ru-RU", "sv-SE", "tr-TR", "uk-UA", "vi-VN", "zh-CN", "zh-TW");
    }

    static void handleInteraction(String actionId, Object interactionType, Object context) {
        Action action = ACTIONS.get(actionId == null ? "" : actionId.toLowerCase(Locale.ROOT));
        if (action == null) {
            finish(context, false);
            return;
        }
        try {
            boolean secondary = String.valueOf(interactionType).toLowerCase(Locale.ROOT).contains("secondary");
            String command = secondary ? action.right() : action.left();
            if (command.isBlank()) {
                finish(context, true);
                return;
            }
            Object player = resolvePlayer(context);
            if (player == null) {
                finish(context, false);
                return;
            }
            executeAsPlayer(player, expandPlaceholders(command, player));
            finish(context, true);
        } catch (Exception ex) {
            System.err.println("[MenuInfoPages] ItemCommands click failed: " + message(ex));
            finish(context, false);
        }
    }

    private static Object resolvePlayer(Object context) throws Exception {
        if (context == null) return null;
        for (String method : List.of("getPlayer", "getPlayerRef", "getSource", "getEntity")) {
            try {
                Object value = invokeNoArg(context, method);
                if (value instanceof PlayerRef) return value;
                if (value != null) {
                    for (String nested : List.of("getPlayerRef", "getPlayer")) {
                        try {
                            Object candidate = invokeNoArg(value, nested);
                            if (candidate instanceof PlayerRef) return candidate;
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object getComponent(Object store, Object reference, Object componentType) throws Exception {
        if (store == null) return null;
        for (Method method : store.getClass().getMethods()) {
            if (!method.getName().equals("getComponent") || method.getParameterCount() != 2) continue;
            return method.invoke(store, reference, componentType);
        }
        return null;
    }

    private static void executeAsPlayer(Object player, String command) throws Exception {
        if (command == null || command.isBlank()) return;
        if (player instanceof PlayerRef playerRef) {
            CommandManager.get().handleCommand(playerRef, command);
            return;
        }
        Object manager = CommandManager.get();
        for (Method method : manager.getClass().getMethods()) {
            if (!method.getName().equals("handleCommand") || method.getParameterCount() != 2) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types[0].isInstance(player) && types[1] == String.class) {
                method.invoke(manager, player, command);
                return;
            }
        }
        throw new NoSuchMethodException("CommandManager.handleCommand(player,String)");
    }

    private static String expandPlaceholders(String command, Object player) {
        if (command == null) return "";
        String name = "";
        String uuid = "";
        try {
            Object value = invokeNoArg(player, "getUsername");
            if (value != null) name = String.valueOf(value);
        } catch (Exception ignored) {
        }
        try {
            Object value = invokeNoArg(player, "getUuid");
            if (value != null) uuid = String.valueOf(value);
        } catch (Exception ignored) {
        }
        return command.replace("{player}", name).replace("{uuid}", uuid);
    }

    private static void finish(Object context, boolean success) {
        if (context == null) return;
        for (String methodName : List.of("finish", "complete")) {
            for (Method method : context.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                try {
                    if (method.getParameterCount() == 0) method.invoke(context);
                    else if (method.getParameterCount() == 1
                            && (method.getParameterTypes()[0] == boolean.class
                            || method.getParameterTypes()[0] == Boolean.class)) {
                        method.invoke(context, success);
                    } else continue;
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = findMethod(target.getClass(), methodName, 0);
        if (method == null) throw new NoSuchMethodException(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
            }
        }
        return null;
    }

    private static String normalizeCommand(String value) {
        String command = value == null ? "" : value.trim();
        while (command.startsWith("/")) command = command.substring(1).trim();
        return command;
    }

    private static String sanitizeId(String value) {
        if (value == null) return "";
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9_-]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        return cleaned.replaceAll("^_+|_+$", "");
    }

    private static String toAssetKeySegment(String value) {
        String cleaned = sanitizeId(value);
        if (cleaned.isBlank()) return "Item";
        StringBuilder out = new StringBuilder(cleaned.length());
        boolean capitalize = true;
        for (char c : cleaned.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalize = true;
                continue;
            }
            out.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        return out.toString();
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String printable(String command) {
        return command == null || command.isBlank() ? "-" : "/" + command;
    }

    private static String message(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static void writeAsset(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    record Action(String left, String right) {
        Action {
            left = Objects.requireNonNullElse(left, "");
            right = Objects.requireNonNullElse(right, "");
        }
    }

    record AssetBlob(byte[] bytes, String description) {}
    record SourceItem(String json, String containerKey, String description) {}
}
