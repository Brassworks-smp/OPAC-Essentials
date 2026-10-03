package brassworks.opac_essentials.command;

import brassworks.opac_essentials.party.menu.server.PartyMenuService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
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
        LiteralCommandNode<CommandSourceStack> parties = dispatcher.register(
                Commands.literal(commandName).executes(context -> {
                    PartyMenuService.sendOpenSnapshot(
                            context.getSource().getPlayerOrException()
                    );
                    return 1;
                })
        );
        if (openPacParties != null) {
            for (CommandNode<CommandSourceStack> child
                    : openPacParties.getChildren()) {
                parties.addChild(child);
            }
        }
    }
}
