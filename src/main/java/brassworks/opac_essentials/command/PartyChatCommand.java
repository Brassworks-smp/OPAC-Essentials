package brassworks.opac_essentials.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import brassworks.opac_essentials.opac_essentials;
import brassworks.opac_essentials.partychat.PartyMessenger;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Predicate;

public final class PartyChatCommand {
    private final String commandName;

    public PartyChatCommand(String commandName) {
        this.commandName = commandName;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Predicate<CommandSourceStack> requirement =
                source -> source.getEntity() instanceof ServerPlayer;

        Command<CommandSourceStack> toggleAction = ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID id = player.getUUID();
            boolean newState = opac_essentials.togglePartyChat(id);
            Component stateText = Component.literal(
                    newState ? "enabled" : "disabled"
            ).withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED);
            player.sendSystemMessage(Component.literal("Party chat is now ").append(stateText));
            return 1;
        };

        Command<CommandSourceStack> statusAction = ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean state = opac_essentials.isPartyChatEnabled(player.getUUID());
            Component stateText = Component.literal(
                    state ? "enabled" : "disabled"
            ).withStyle(state ? ChatFormatting.GREEN : ChatFormatting.RED);
            player.sendSystemMessage(
                    Component.literal("Party chat has been ").append(stateText)
            );
            return 1;
        };

        Command<CommandSourceStack> messageAction = ctx -> {
            String message = StringArgumentType.getString(ctx, "message");
            return PartyMessenger.sendPartyMessage(ctx.getSource(), message);
        };

        dispatcher.register(
                Commands.literal(commandName)
                        .requires(requirement)
                        .then(Commands.literal("toggle").executes(toggleAction))
                        .then(Commands.literal("status").executes(statusAction))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(messageAction))
        );
    }
}
