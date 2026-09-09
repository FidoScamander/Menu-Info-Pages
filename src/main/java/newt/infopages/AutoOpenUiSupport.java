package newt.infopages;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

final class AutoOpenUiSupport {
    private static final String ACTION = "ToggleDontShow";
    private AutoOpenUiSupport() {}

    static void build(PlayerRef player, PageDefinition page, UICommandBuilder commands, UIEventBuilder events) {
        if (player == null || page == null || commands == null || events == null) return;
        boolean dismissible = AutoOpenManager.isDismissible(page.command);
        commands.set("#DontShowRow.Visible", dismissible);
        if (!dismissible) return;
        commands.set("#DontShowAgainLabel.Text", DismissLocalization.text(player));
        commands.set("#DontShowAgainCheck.Value", AutoOpenManager.isDismissed(player, page.command));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DontShowAgainCheck",
                EventData.of("Action", ACTION), false);
    }

    static void preHandle(PlayerRef player, PageDefinition page, InfoPageUI.PageData data) {
        if (data == null || !ACTION.equals(data.Action)) return;
        AutoOpenManager.toggleDismissed(player, page == null ? "" : page.command);
        data.Action = "";
    }
}
