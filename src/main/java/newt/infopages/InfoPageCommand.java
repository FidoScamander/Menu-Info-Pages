package newt.infopages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class InfoPageCommand extends AbstractPlayerCommand {
    private final PageDefinition page;

    InfoPageCommand(PageDefinition page) {
        super(page.command, page.description);
        this.page = page;
        setPermissionGroups("hytale:Adventurer", "hytale:WorldEditor");
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (!PagePermissionSupport.manualOpenAllowed(player, playerRef, page.command)) return;
        player.getPageManager().openCustomPage(ref, store, new InfoPageUI(playerRef, page));
    }
}
