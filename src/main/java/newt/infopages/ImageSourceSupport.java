package newt.infopages;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ImageSourceSupport {
    private static final int RECOMMENDED_MAX_WIDTH = 700;
    private static final long WARN_FILE_BYTES = 1024L * 1024L;
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
    private static final Path CACHE_ROOT = Path.of("mods", "MenuInfoPages", "cache", "remote-assets");
    private static final Path IMAGE_DIR = CACHE_ROOT.resolve(Path.of("Common", "UI", "Custom", "Pages", "Remote"));
    private static final String ASSET_PREFIX = "UI/Custom/Pages/Remote/";
    private static final Object LOCK = new Object();
    private static volatile Map<String, CacheEntry> byUrl = Map.of();
    private static volatile Map<String, CacheEntry> byAsset = Map.of();
    private static volatile Object pluginInstance;
    private static volatile boolean packRegistered;

    private ImageSourceSupport() {}

    static void initialize(Object plugin) {
        pluginInstance = plugin;
        PersistentImagePackSupport.ensureManifest(CACHE_ROOT);
        LocalImageSupport.refresh();
        registerRuntimePack();
    }

    static void refreshPages(List<PageDefinition> pages) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(IMAGE_DIR);
                Map<String, List<String>> namesToUrls = new HashMap<>();
                List<String> requested = collectUrls(pages);
                for (String url : requested) namesToUrls.computeIfAbsent(fileName(url), key -> new ArrayList<>()).add(url);
                namesToUrls.forEach((name, urls) -> {
                    if (urls.stream().distinct().count() > 1) {
                        throw new IllegalArgumentException("Remote image filename conflict for " + name + ": " + urls);
                    }
                });

                Map<String, CacheEntry> next = new LinkedHashMap<>();
                Set<Path> keep = new HashSet<>();
                for (String url : requested) {
                    CacheEntry previous = byUrl.get(url);
                    CacheEntry entry = downloadOrReuse(url, previous);
                    next.put(url, entry);
                    keep.add(entry.file);
                }
                try (var stream = Files.list(IMAGE_DIR)) {
                    stream.filter(Files::isRegularFile).filter(path -> !keep.contains(path)).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
                }
                byUrl = Map.copyOf(next);
                Map<String, CacheEntry> assets = new HashMap<>();
                next.values().forEach(entry -> assets.put(entry.assetPath.toLowerCase(Locale.ROOT), entry));
                byAsset = Map.copyOf(assets);
                if (!packRegistered) registerRuntimePack();
            } catch (Exception ex) {
                System.err.println("[MenuInfoPages] Remote image refresh failed: " + rootMessage(ex));
            }
        }
    }

    static String resolveSource(String source) {
        if (source == null || source.isBlank()) return source;
        String local = LocalImageSupport.resolve(source);
        if (!local.equals(source)) return local;
        if (isExternal(source)) {
            CacheEntry entry = byUrl.get(source.strip());
            return entry == null ? source : entry.assetPath;
        }
        return normalizeResourcePath(source);
    }

    static int[] readImageSize(String source) {
        if (source == null || source.isBlank()) return null;
        int[] local = LocalImageSupport.readImageSize(source);
        if (local != null) return local;
        CacheEntry entry = isExternal(source) ? byUrl.get(source.strip()) : byAsset.get(normalizeResourcePath(source).toLowerCase(Locale.ROOT));
        if (entry != null) return new int[]{entry.width, entry.height};
        return readBundledImageSize(normalizeResourcePath(source));
    }

    private static CacheEntry downloadOrReuse(String url, CacheEntry previous) throws Exception {
        String name = fileName(url);
        Path target = IMAGE_DIR.resolve(name).normalize();
        if (!target.getParent().equals(IMAGE_DIR)) throw new IOException("Unsafe remote image name: " + name);
        if (previous != null && Files.isRegularFile(previous.file) && previous.url.equals(url) && !ReloadSettings.reloadImagesEnabled()) return previous;

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "MenuInfoPages/1.3.0")
                .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("HTTP " + response.statusCode() + " for " + url);
        byte[] bytes = response.body();
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Remote PNG exceeds 8 MiB: " + url);
        ImageInfo info = inspectImage(bytes, url);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "mip-", ".png");
        try {
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        CacheEntry entry = new CacheEntry(url, target, ASSET_PREFIX + name, info.width, info.height, bytes.length);
        warnIfLarge(entry);
        return entry;
    }

    private static ImageInfo inspectImage(byte[] bytes, String source) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("Remote image is not a valid PNG: " + source);
            if (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
                throw new IOException("Only PNG remote images are supported: " + source);
            }
            return new ImageInfo(image.getWidth(), image.getHeight());
        }
    }

    private static int[] readBundledImageSize(String path) {
        if (path == null || path.isBlank()) return null;
        String resource = path.startsWith("Common/") ? "/" + path : "/Common/" + path;
        try (InputStream in = ImageSourceSupport.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            BufferedImage image = ImageIO.read(in);
            return image == null ? null : new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void warnIfLarge(CacheEntry entry) {
        if (entry.width > RECOMMENDED_MAX_WIDTH) System.err.println("[MenuInfoPages] Remote PNG is wider than recommended (" + entry.width + "px): " + entry.url);
        if (entry.bytes > WARN_FILE_BYTES) System.err.println("[MenuInfoPages] Remote PNG is larger than 1 MiB: " + entry.url);
    }

    private static void registerRuntimePack() {
        if (packRegistered || pluginInstance == null) return;
        try {
            PersistentImagePackSupport.ensureManifest(CACHE_ROOT);
            Files.createDirectories(IMAGE_DIR);
            Class<?> moduleType = Class.forName("com.hypixel.hytale.server.core.asset.AssetModule");
            Object module = moduleType.getMethod("get").invoke(null);
            if (module == null) return;
            Object baseManifest = pluginInstance.getClass().getMethod("getManifest").invoke(pluginInstance);
            Class<?> manifestType = Class.forName("com.hypixel.hytale.common.plugin.PluginManifest");
            Object manifest = manifestType.getConstructor().newInstance();
            ManifestCompat.copyBase(manifest, baseManifest);
            setString(manifest, "setGroup", "Newt");
            setString(manifest, "setName", "MenuInfoPagesRemote");
            setString(manifest, "setDescription", "MenuInfoPages cached remote UI images");

            for (Method method : module.getClass().getMethods()) {
                if (!method.getName().equals("registerPack") || method.getParameterCount() != 4) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types[0] != String.class || !Path.class.isAssignableFrom(types[1])) continue;
                if (!types[2].getName().equals("com.hypixel.hytale.common.plugin.PluginManifest")) continue;
                Class<? extends Enum> enumType = types[3].asSubclass(Enum.class);
                @SuppressWarnings({"unchecked", "rawtypes"}) Object source = Enum.valueOf((Class) enumType, "MODS");
                method.invoke(module, "Newt:MenuInfoPagesRemote", CACHE_ROOT, manifest, source);
                packRegistered = true;
                System.out.println("[MenuInfoPages] Image asset pack registered from " + CACHE_ROOT + ".");
                return;
            }
            System.err.println("[MenuInfoPages] Compatible AssetModule.registerPack overload not found.");
        } catch (Throwable ex) {
            System.err.println("[MenuInfoPages] Image asset pack registration failed: " + rootMessage(ex));
        }
    }

    private static void setString(Object object, String method, String value) {
        try { object.getClass().getMethod(method, String.class).invoke(object, value); }
        catch (ReflectiveOperationException ignored) {}
    }

    private static List<String> collectUrls(List<PageDefinition> pages) {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PageDefinition page : pages) {
            for (String image : page.images) if (isExternal(image) && seen.add(image.strip())) urls.add(image.strip());
            for (PageDefinition.Section section : page.sections) if (isExternal(section.image()) && seen.add(section.image().strip())) urls.add(section.image().strip());
        }
        return urls;
    }

    private static String fileName(String url) {
        try {
            String path = URI.create(url).getPath();
            String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) name = "remote-" + sha256(url).substring(0, 12) + ".png";
            if (name.length() > 120) name = name.substring(0, 100) + "-" + sha256(url).substring(0, 12) + ".png";
            return name;
        } catch (Exception ex) {
            return "remote-" + Integer.toHexString(url.hashCode()) + ".png";
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format("%02x", b));
        return out.toString();
    }

    private static boolean isExternal(String value) {
        if (value == null) return false;
        String text = value.strip().toLowerCase(Locale.ROOT);
        return text.startsWith("https://") || text.startsWith("http://");
    }

    static String normalizeResourcePath(String value) {
        if (value == null) return "";
        String path = value.replace('\\', '/').strip();
        while (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("Common/")) path = path.substring(7);
        return path;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private record CacheEntry(String url, Path file, String assetPath, int width, int height, long bytes) {}
    private record ImageInfo(int width, int height) {}
}
