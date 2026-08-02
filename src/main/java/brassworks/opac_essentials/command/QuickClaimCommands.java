package brassworks.opac_essentials.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class QuickClaimCommands {
    private final String claimCommandName;
    private final String unclaimCommandName;

    public QuickClaimCommands(String claimCommandName, String unclaimCommandName) {
        this.claimCommandName = claimCommandName;
        this.unclaimCommandName = unclaimCommandName;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> openPacClaims =
                dispatcher.getRoot().getChild("openpac-claims");
        if (openPacClaims == null) {
            return;
        }
        registerCurrentChunkCommand(
                dispatcher, openPacClaims, "claim", claimCommandName
        );
        registerCurrentChunkCommand(
                dispatcher, openPacClaims, "unclaim", unclaimCommandName
        );
    }

    private void registerCurrentChunkCommand(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandNode<CommandSourceStack> openPacClaims,
            String openPacAction,
            String commandName
    ) {
        if (commandName == null) {
            return;
        }
        CommandNode<CommandSourceStack> action = openPacClaims.getChild(openPacAction);
        if (action == null) {
            return;
        }
        Command<CommandSourceStack> command = action.getCommand();
        if (command == null) {
            return;
        }
        dispatcher.register(
                Commands.literal(commandName)
                        .requires(action.getRequirement())
                        .executes(command)
        );
    }
}
