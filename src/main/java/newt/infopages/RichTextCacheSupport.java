package newt.infopages;

import com.hypixel.hytale.server.core.Message;
import java.util.concurrent.ConcurrentHashMap;

final class RichTextCacheSupport {
    private static final ConcurrentHashMap<String, Message> CACHE = new ConcurrentHashMap<>();
    private RichTextCacheSupport() {}
    static void clear() { CACHE.clear(); }
    static int size() { return CACHE.size(); }
    static Message format(String source) {
        String key = source == null ? "" : source;
        return CACHE.computeIfAbsent(key, RichTextFormatter::format);
    }
}
