package newt.infopages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

/** Interaction used by generated ItemCommands assets. */
public final class ItemCommandInteraction extends SimpleInstantInteraction {
    public static final String TYPE_NAME = "MenuInfoPages_ItemCommand";
    public static final BuilderCodec<ItemCommandInteraction> CODEC = buildCodec();

    private String actionId = "";

    private static BuilderCodec<ItemCommandInteraction> buildCodec() {
        BuilderCodec.Builder<ItemCommandInteraction> builder = BuilderCodec.builder(
                ItemCommandInteraction.class,
                ItemCommandInteraction::new,
                SimpleInstantInteraction.CODEC);
        builder.addField(new KeyedCodec<>("ActionId", Codec.STRING),
                ItemCommandInteraction::setActionId,
                ItemCommandInteraction::getActionId);
        builder.documentation("Executes the MenuInfoPages command configured for a generated item and click type.");
        return builder.build();
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId == null ? "" : actionId;
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        ItemCommandSupport.handleInteraction(actionId, type, context);
    }
}
