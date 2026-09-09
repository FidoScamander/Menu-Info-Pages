package newt.infopages;

import com.hypixel.hytale.server.core.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichTextFormatter {
    private static final Pattern TAG = Pattern.compile("\\{(/|b|i|m|#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3}))\\}");
    private static final int BOLD = 1;
    private static final int ITALIC = 2;
    private static final int MONO = 3;

    private RichTextFormatter() {}

    static Message format(String source) {
        if (source == null || source.isEmpty()) return Message.raw("");

        List<Message> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        List<Object> styles = new ArrayList<>();
        Matcher matcher = TAG.matcher(source);
        int cursor = 0;

        while (matcher.find()) {
            text.append(source, cursor, matcher.start());
            String token = matcher.group(1);
            switch (token) {
                case "/" -> {
                    flush(text, styles, parts);
                    if (!styles.isEmpty()) styles.remove(styles.size() - 1);
                }
                case "b" -> { flush(text, styles, parts); styles.add(BOLD); }
                case "i" -> { flush(text, styles, parts); styles.add(ITALIC); }
                case "m" -> { flush(text, styles, parts); styles.add(MONO); }
                default -> {
                    if (token.startsWith("#")) {
                        String hex = matcher.group(2);
                        if (hex != null && !hex.isEmpty()) {
                            if (hex.length() == 3) {
                                hex = "" + hex.charAt(0) + hex.charAt(0)
                                        + hex.charAt(1) + hex.charAt(1)
                                        + hex.charAt(2) + hex.charAt(2);
                            }
                            flush(text, styles, parts);
                            styles.add("#" + hex);
                        } else {
                            text.append(matcher.group());
                        }
                    } else {
                        text.append(matcher.group());
                    }
                }
            }
            cursor = matcher.end();
        }

        text.append(source.substring(cursor));
        flush(text, styles, parts);
        if (parts.isEmpty()) return Message.raw("");
        if (parts.size() == 1) return parts.getFirst();
        return Message.join(parts.toArray(Message[]::new));
    }

    static String strip(String source) {
        return source == null || source.isEmpty() ? "" : TAG.matcher(source).replaceAll("");
    }

    private static void flush(StringBuilder text, List<Object> styles, List<Message> parts) {
        String value = text.toString();
        text.setLength(0);
        if (value.isEmpty()) return;

        Message message = Message.raw(value);
        String color = null;
        for (Object style : styles) if (style instanceof String s) color = s;
        if (color != null) message.color(color);

        for (Object style : styles) {
            if (!(style instanceof Integer flag)) continue;
            if (flag == BOLD) message.bold(true);
            else if (flag == ITALIC) message.italic(true);
            else if (flag == MONO) message.monospace(true);
        }
        parts.add(message);
    }
}
