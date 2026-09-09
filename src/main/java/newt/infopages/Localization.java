package newt.infopages;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

final class Localization {
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("ar", "ar-SA"), Map.entry("da", "da-DK"), Map.entry("de", "de-DE"),
            Map.entry("en", "en-US"), Map.entry("es", "es-ES"), Map.entry("fi", "fi-FI"),
            Map.entry("fr", "fr-FR"), Map.entry("hu", "hu-HU"), Map.entry("id", "id-ID"),
            Map.entry("it", "it-IT"), Map.entry("ja", "ja-JP"), Map.entry("ko", "ko-KR"),
            Map.entry("nl", "nl-NL"), Map.entry("no", "no-NO"), Map.entry("nb", "no-NO"),
            Map.entry("nn", "no-NO"), Map.entry("pl", "pl-PL"), Map.entry("pt", "pt-PT"),
            Map.entry("ro", "ro-RO"), Map.entry("ru", "ru-RU"), Map.entry("sv", "sv-SE"),
            Map.entry("tr", "tr-TR"), Map.entry("uk", "uk-UA"), Map.entry("vi", "vi-VN"),
            Map.entry("zh", "zh-CN")
    );

    private Localization() {}

    static String dismissLabel(PlayerRef player) {
        return read(player, "menuinfopages.autoopen.dont_show_again", "Do not show again");
    }

    static String permissionDenied(Object player) {
        return read(player, "menuinfopages.page.no_permission", "You do not have permission to open this page.");
    }

    private static String read(Object player, String key, String fallback) {
        String locale = "en-US";
        try {
            Object value = player == null ? null : player.getClass().getMethod("getLanguage").invoke(player);
            if (value instanceof String language && !language.isBlank()) locale = normalize(language);
        } catch (ReflectiveOperationException ignored) {
            // Older server builds may not expose a client language on this object.
        }
        String localized = readBundled(locale, key);
        if (usable(localized, key)) return localized;
        localized = readBundled("en-US", key);
        return usable(localized, key) ? localized : fallback;
    }

    static String normalize(String language) {
        if (language == null || language.isBlank()) return "en-US";
        String normalized = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.equals("pt-br") || normalized.startsWith("pt-br-")) return "pt-BR";
        if (normalized.startsWith("zh-hant") || normalized.startsWith("zh-tw")
                || normalized.startsWith("zh-hk") || normalized.startsWith("zh-mo")) return "zh-TW";
        String primary = normalized.contains("-") ? normalized.substring(0, normalized.indexOf('-')) : normalized;
        return LANGUAGES.getOrDefault(primary, "en-US");
    }

    private static String readBundled(String language, String key) {
        String resource = "/Server/Languages/" + language + "/server.lang";
        try (InputStream in = Localization.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) continue;
                    if (key.equals(trimmed.substring(0, separator).trim())) {
                        return trimmed.substring(separator + 1).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean usable(String value, String key) {
        return value != null && !value.isBlank() && !value.equals(key);
    }
}
