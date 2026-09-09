package newt.infopages;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class DelayedCommandExecutor {
    private static final long DELAY_MILLIS = 50L;
    private DelayedCommandExecutor() {}

    static void execute(PlayerRef player, String runAs, String command) {
        if (player == null || command == null || command.isBlank()) return;
        Thread.startVirtualThread(() -> {
            try { Thread.sleep(DELAY_MILLIS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
            runOnWorldThread(player, () -> {
                try {
                    if ("console".equalsIgnoreCase(runAs)) CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
                    else CommandManager.get().handleCommand(player, command);
                } catch (RuntimeException ex) {
                    System.err.println("[MenuInfoPages] Button command failed: " + command + " (" + ex.getMessage() + ")");
                }
            });
        });
    }

    private static void runOnWorldThread(PlayerRef player, Runnable task) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            Object external = ref.getStore().getExternalData();
            if (external instanceof EntityStore entityStore && entityStore.getWorld() != null) entityStore.getWorld().execute(task);
        } catch (Throwable ignored) {
        }
    }
}
