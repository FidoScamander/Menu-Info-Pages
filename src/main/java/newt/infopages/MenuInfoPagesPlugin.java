package newt.infopages;

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/** Entry point declared by manifest.json. */
public final class MenuInfoPagesPlugin extends NewtInfoPagesPlugin {
    public MenuInfoPagesPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        FreeConfigSupport.ensure();
        ItemCommandSupport.prepareAssetsBeforePackRegistration();
        super.setup();

        ImageSourceSupport.initialize(this);
        FullFeatureBridge.setPlugin(this);
        ItemCommandSupport.initializeFromBridge();
        ReloadSettings.initialize();
        AutoOpenManager.initialize(getCommandRegistry());
        getCommandRegistry().registerCommand(new InfoPagesCommand());

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, AutoOpenManager::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, AutoOpenManager::onPlayerDisconnect);

        System.out.println("[MenuInfoPages] /infopages newpage <name> <command> and /infopages reload registered. "
                + "Local/remote PNG live reload is controlled by mods/MenuInfoPages/reload.yml.");
        ReleaseDocsSupport.writeReadme();
        FreeUiPrewarmSupport.warmup();
    }
}
