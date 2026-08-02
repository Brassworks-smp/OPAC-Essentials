package brassworks.opac_essentials.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class OpenClaimsCommand {
    private final String commandName;

    public OpenClaimsCommand(String commandName) {
        this.commandName = commandName;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> openPacClaims =
                dispatcher.getRoot().getChild("openpac-claims");
        LiteralCommandNode<CommandSourceStack> claims = dispatcher.register(
                Commands.literal(commandName)
                        .then(new ClaimPermissionsCommand().create())
        );

        if (openPacClaims != null) {
            for (CommandNode<CommandSourceStack> child : openPacClaims.getChildren()) {
                claims.addChild(child);
            }
        }
    }
}
