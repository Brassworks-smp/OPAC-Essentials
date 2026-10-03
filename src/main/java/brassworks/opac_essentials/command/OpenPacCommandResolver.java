package brassworks.opac_essentials.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;

final class OpenPacCommandResolver {
    private OpenPacCommandResolver() {
    }

    static CommandNode<CommandSourceStack> findClaims(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        return find(dispatcher, "oclaims", "openpac-claims");
    }

    static CommandNode<CommandSourceStack> findParties(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        return find(dispatcher, "oparties", "openpac-parties");
    }

    private static CommandNode<CommandSourceStack> find(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String... commandNames
    ) {
        for (String commandName : commandNames) {
            CommandNode<CommandSourceStack> command =
                    dispatcher.getRoot().getChild(commandName);
            if (command != null) {
                return command;
            }
        }
        return null;
    }
}
