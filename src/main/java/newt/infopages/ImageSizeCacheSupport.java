package newt.infopages;

import java.util.concurrent.ConcurrentHashMap;

final class ImageSizeCacheSupport {
    private static final int[] MISSING = new int[0];
    private static final ConcurrentHashMap<String, int[]> CACHE = new ConcurrentHashMap<>();
    private ImageSizeCacheSupport() {}
    static int[] readImageSize(String source) {
        if (source == null || source.isBlank()) return null;
        int[] size = CACHE.computeIfAbsent(source, key -> {
            int[] resolved = ImageSourceSupport.readImageSize(key);
            return resolved == null ? MISSING : resolved;
        });
        return size == MISSING ? null : size.clone();
    }
    static void clear() { CACHE.clear(); }
    static int size() { return CACHE.size(); }
}
