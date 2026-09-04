package brassworks.opac_essentials.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class OpenPartiesCommand {
    private final String commandName;

    public OpenPartiesCommand(String commandName) {
        this.commandName = commandName;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> openPacParties =
                OpenPacCommandResolver.findParties(dispatcher);
        if (openPacParties != null) {
            dispatcher.register(Commands.literal(commandName).redirect(openPacParties));
        }
    }
}
