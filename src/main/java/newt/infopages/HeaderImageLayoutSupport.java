package newt.infopages;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

final class HeaderImageLayoutSupport {
    private HeaderImageLayoutSupport() {}
    static UICommandBuilder setHeaderImageVisible(UICommandBuilder builder, String selector, boolean visible) {
        builder.set(selector + ".Visible", visible);
        return builder;
    }
}
