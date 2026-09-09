package newt.infopages;

import java.util.Iterator;
import java.util.Map;

final class JsonWriter {
    private JsonWriter() {}

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value, 0);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value, int indent) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String s) { quote(out, s); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            if (!map.isEmpty()) out.append('\n');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                out.append("  ".repeat(indent + 1));
                quote(out, String.valueOf(entry.getKey()));
                out.append(": ");
                append(out, entry.getValue(), indent + 1);
                if (it.hasNext()) out.append(',');
                out.append('\n');
            }
            if (!map.isEmpty()) out.append("  ".repeat(indent));
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> values) {
            out.append('[');
            Iterator<?> it = values.iterator();
            if (it.hasNext()) out.append('\n');
            boolean any = false;
            while (it.hasNext()) {
                any = true;
                out.append("  ".repeat(indent + 1));
                append(out, it.next(), indent + 1);
                if (it.hasNext()) out.append(',');
                out.append('\n');
            }
            if (any) out.append("  ".repeat(indent));
            out.append(']');
            return;
        }
        quote(out, String.valueOf(value));
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        quote(out, value);
        return out.substring(1, out.length() - 1);
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
