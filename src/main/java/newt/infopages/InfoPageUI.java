package newt.infopages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Locale;
import java.util.UUID;

final class InfoPageUI extends InteractiveCustomUIPage<InfoPageUI.PageData> {
    private static final int IMAGE_MARGIN = 6;
    private final PageDefinition page;
    private final PlayerRef owner;

    InfoPageUI(PlayerRef owner, PageDefinition page) {
        super(owner, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.owner = owner;
        this.page = page;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        FreeUiProfiler.start();
        commands.append("Pages/MenuInfoPage.ui");
        setRichText(commands, "#Titul", page.title);
        setRichText(commands, "#Intro", page.intro);
        commands.set("#Intro.Visible", !page.intro.isBlank());
        setRichText(commands, "#Footer", page.footer);
        commands.set("#Footer.Visible", !page.footer.isBlank());

        boolean anyTopImage = false;
        for (int i = 0; i < 5; i++) {
            int number = i + 1;
            String slot = "#ImageSlot" + number;
            String imageSelector = "#Image" + number;
            if (i >= page.images.size()) {
                HeaderImageLayoutSupport.setHeaderImageVisible(commands, slot, false);
                commands.set(imageSelector + ".Visible", false);
                setImageAnchor(commands, imageSelector, 0, 0);
                continue;
            }
            String source = ImageSourceSupport.resolveSource(page.images.get(i));
            int[] size = ImageSizeCacheSupport.readImageSize(source);
            boolean visible = size != null;
            HeaderImageLayoutSupport.setHeaderImageVisible(commands, slot, visible);
            commands.set(imageSelector + ".Visible", visible);
            if (visible) {
                commands.set(imageSelector + ".Source", source);
                setImageAnchor(commands, imageSelector, size[0], size[1]);
                anyTopImage = true;
            } else {
                setImageAnchor(commands, imageSelector, 0, 0);
            }
        }
        commands.set("#ImageRow.Visible", anyTopImage);

        for (int i = 0; i < 5; i++) {
            int number = i + 1;
            String sectionSelector = "#Section" + number;
            String titleSelector = "#SectionTitle" + number;
            String textSelector = "#SectionText" + number;
            String imageRow = "#SectionImageRow" + number;
            String imageSelector = "#SectionImage" + number;

            if (i >= page.sections.size()) {
                commands.set(sectionSelector + ".Visible", false);
                commands.set(imageRow + ".Visible", false);
                commands.set(imageSelector + ".Visible", false);
                setImageAnchor(commands, imageSelector, 0, 0);
                continue;
            }

            PageDefinition.Section section = page.sections.get(i);
            boolean titleVisible = !section.title().isBlank();
            boolean textVisible = !section.text().isBlank();
            String imageSource = ImageSourceSupport.resolveSource(section.image());
            int[] imageSize = ImageSizeCacheSupport.readImageSize(imageSource);
            boolean imageVisible = imageSize != null;
            commands.set(sectionSelector + ".Visible", titleVisible || textVisible || imageVisible);
            commands.set(titleSelector + ".Visible", titleVisible);
            commands.set(textSelector + ".Visible", textVisible);
            setRichText(commands, titleSelector, section.title());
            setRichText(commands, textSelector, section.text());
            commands.set(imageRow + ".Visible", imageVisible);
            commands.set(imageSelector + ".Visible", imageVisible);
            if (imageVisible) {
                commands.set(imageSelector + ".Source", imageSource);
                setImageAnchor(commands, imageSelector, imageSize[0], imageSize[1]);
            } else {
                setImageAnchor(commands, imageSelector, 0, 0);
            }
        }

        commands.set("#ButtonRow.Visible", !page.buttons.isEmpty());
        for (int i = 0; i < 5; i++) {
            int number = i + 1;
            for (String suffix : new String[]{"Primary", "Secondary", "Danger"}) {
                commands.set("#Button" + number + suffix + ".Visible", false);
            }
            if (i >= page.buttons.size()) continue;
            PageDefinition.Button button = page.buttons.get(i);
            String suffix = switch (button.style().toLowerCase(Locale.ROOT)) {
                case "primary" -> "Primary";
                case "danger" -> "Danger";
                default -> "Secondary";
            };
            String selector = "#Button" + number + suffix;
            commands.set(selector + ".Text", button.text());
            commands.set(selector + ".Visible", true);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                    EventData.of("Action", "button:" + i), false);
        }
        AutoOpenUiSupport.build(owner, page, commands, events);
        FreeUiProfiler.end(page.command);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        super.handleDataEvent(ref, store, data);
        AutoOpenUiSupport.preHandle(owner, page, data);
        if (data == null || data.Action == null || !data.Action.startsWith("button:")) return;

        final int index;
        try { index = Integer.parseInt(data.Action.substring("button:".length())); }
        catch (NumberFormatException ex) { return; }
        if (index < 0 || index >= page.buttons.size()) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        PageDefinition.Button button = page.buttons.get(index);
        String command = prepareCommand(button.command());
        if (command.isEmpty()) {
            if (button.close()) close();
            return;
        }
        if (button.close()) {
            close();
            DelayedCommandExecutor.execute(owner, button.runAs(), command);
        } else {
            DelayedCommandExecutor.execute(owner, button.runAs(), command);
        }
    }

    private String prepareCommand(String command) {
        String value = command == null ? "" : command.strip();
        while (value.startsWith("/")) value = value.substring(1);
        String username = owner.getUsername() == null ? "" : owner.getUsername();
        UUID uuid = owner.getUuid();
        return value.replace("{player}", username).replace("{uuid}", uuid == null ? "" : uuid.toString());
    }

    private static void setRichText(UICommandBuilder commands, String selector, String value) {
        commands.set(selector + ".Text", RichTextCacheSupport.format(value == null ? "" : value));
    }

    private static void setImageAnchor(UICommandBuilder commands, String selector, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        anchor.setLeft(Value.of(width > 0 ? IMAGE_MARGIN : 0));
        anchor.setRight(Value.of(width > 0 ? IMAGE_MARGIN : 0));
        commands.setObject(selector + ".Anchor", anchor);
    }

    public static final class PageData {
        public static final BuilderCodec.Builder<PageData> BUILDER = BuilderCodec.builder(PageData.class, PageData::new);
        public static final BuilderCodec<PageData> CODEC;
        public String Action;
        static {
            BUILDER.addField(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.Action = value, data -> data.Action);
            CODEC = BUILDER.build();
        }
    }
}
