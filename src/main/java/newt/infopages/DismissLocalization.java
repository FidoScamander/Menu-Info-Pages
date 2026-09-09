package newt.infopages;

import com.hypixel.hytale.server.core.universe.PlayerRef;

final class DismissLocalization {
    private DismissLocalization() {}
    static String text(PlayerRef player) { return Localization.dismissLabel(player); }
    static String normalize(String language) { return Localization.normalize(language); }
}
