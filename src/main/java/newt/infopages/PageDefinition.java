package newt.infopages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PageDefinition {
    final String command;
    final String description;
    final String title;
    final String intro;
    final List<String> images;
    final List<Section> sections;
    final List<Button> buttons;
    final String footer;

    PageDefinition(String command, String description, String title, String intro,
                   List<String> images, List<Section> sections, List<Button> buttons,
                   String footer) {
        this.command = normalizeCommand(command);
        this.description = clean(description, "Open an information page");
        this.title = clean(title, this.command);
        this.intro = clean(intro, "");
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
        this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        this.buttons = Collections.unmodifiableList(new ArrayList<>(buttons));
        this.footer = clean(footer, "Press Esc to close.");
    }

    static PageDefinition fromJson(Map<String, Object> root) {
        String command = string(root.get("command"));
        String description = string(root.get("description"));
        String title = string(root.get("title"));
        String intro = string(root.get("intro"));
        String footer = string(root.get("footer"));

        List<String> images = new ArrayList<>(5);
        if (root.get("images") instanceof List<?> rawImages) {
            for (Object value : rawImages) {
                String image = string(value).strip();
                if (!image.isEmpty() && images.size() < 5) images.add(image);
            }
        }

        List<Section> sections = new ArrayList<>(5);
        if (root.get("sections") instanceof List<?> rawSections) {
            for (Object value : rawSections) {
                if (!(value instanceof Map<?, ?> raw) || sections.size() >= 5) continue;
                String sectionTitle = string(raw.get("title")).strip();
                String text = string(raw.get("text")).strip();
                String image = string(raw.get("image")).strip();
                if (!sectionTitle.isEmpty() || !text.isEmpty() || !image.isEmpty()) {
                    sections.add(new Section(sectionTitle, text, image));
                }
            }
        }

        List<Button> buttons = new ArrayList<>(5);
        if (root.get("buttons") instanceof List<?> rawButtons) {
            for (Object value : rawButtons) {
                if (!(value instanceof Map<?, ?> raw) || buttons.size() >= 5) continue;
                String text = string(raw.get("text")).strip();
                if (text.isEmpty()) continue;
                String commandValue = string(raw.get("command")).strip();
                String runAs = string(raw.get("runAs")).strip().toLowerCase(Locale.ROOT);
                if (!runAs.equals("console")) runAs = "player";
                String style = string(raw.get("style")).strip().toLowerCase(Locale.ROOT);
                if (!style.equals("primary") && !style.equals("danger")) style = "secondary";
                buttons.add(new Button(text, commandValue, runAs, bool(raw.get("close"), true), style));
            }
        }

        return new PageDefinition(command, description, title, intro, images, sections, buttons, footer);
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    static String normalizeCommand(String value) {
        String command = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        while (command.startsWith("/")) command = command.substring(1);
        if (!command.matches("[a-z0-9][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Invalid page command: " + value);
        }
        return command;
    }

    record Section(String title, String text, String image) {}
    record Button(String text, String command, String runAs, boolean close, String style) {}
}
