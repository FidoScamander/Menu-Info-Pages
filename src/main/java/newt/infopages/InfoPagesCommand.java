package newt.infopages;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class InfoPagesCommand extends AbstractCommandCollection {
    InfoPagesCommand() {
        super("infopages", "Manage MenuInfoPages");
        addSubCommand(new NewPageCommand());
        addSubCommand(new ReloadCommand());
    }

    private static final class NewPageCommand extends AbstractAsyncCommand {
        private final RequiredArg<String> nameArg;
        private final RequiredArg<String> commandArg;

        private NewPageCommand() {
            super("newpage", "Create a new MenuInfoPages JSON template");
            nameArg = withRequiredArg("name", "JSON page filename (without path)", ArgTypes.STRING);
            commandArg = withRequiredArg("command", "Player command for the page", ArgTypes.STRING);
            requirePermission("newt.infopages.newpage");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            try {
                String name = context.get(nameArg);
                String command = context.get(commandArg);
                Path created = AutoOpenManager.createNewPage(name, command);
                AutoOpenManager.ReloadResult result = AutoOpenManager.reloadFromDisk(true);
                context.sendMessage(Message.raw("[MenuInfoPages] Created " + created + " as /"
                        + AutoOpenManager.normalizeCommandInput(command) + ". " + result.summary()));
            } catch (Throwable ex) {
                context.sendMessage(Message.raw("[MenuInfoPages] Could not create page: " + ex.getMessage()));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ReloadCommand extends AbstractAsyncCommand {
        private ReloadCommand() {
            super("reload", "Reload MenuInfoPages JSON pages and auto-open settings");
            requirePermission("newt.infopages.reload");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext context) {
            try {
                AutoOpenManager.ReloadResult result = AutoOpenManager.reloadFromDisk(true);
                context.sendMessage(Message.raw("[MenuInfoPages] Reloaded: " + result.summary()));
            } catch (Throwable ex) {
                context.sendMessage(Message.raw("[MenuInfoPages] Reload failed: " + ex.getMessage()));
            }
            FreeUiPrewarmSupport.warmup();
            return CompletableFuture.completedFuture(null);
        }
    }
}
