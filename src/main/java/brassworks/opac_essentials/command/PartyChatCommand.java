package brassworks.opac_essentials.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import brassworks.opac_essentials.partychat.PartyMessenger;
import brassworks.opac_essentials.partychat.PartyChatSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

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
            if (OpenPACServerAPI.get(player.server).getPartyManager()
                    .getPartyByMember(player.getUUID()) == null) {
                player.sendSystemMessage(
                        Component.literal("You are not in a party.")
                                .withStyle(ChatFormatting.RED)
                );
                PartyChatSavedData.get(player.server)
                        .setEnabled(player.getUUID(), false);
                return 0;
            }
            UUID id = player.getUUID();
            boolean newState = PartyChatSavedData.get(player.server).toggle(id);
            Component stateText = Component.literal(
                    newState ? "enabled" : "disabled"
            ).withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED);
            player.sendSystemMessage(Component.literal("Party chat is now ").append(stateText));
            return 1;
        };

        Command<CommandSourceStack> statusAction = ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PartyChatSavedData data = PartyChatSavedData.get(player.server);
            boolean hasParty = OpenPACServerAPI.get(player.server)
                    .getPartyManager().getPartyByMember(player.getUUID()) != null;
            if (!hasParty) {
                data.setEnabled(player.getUUID(), false);
            }
            boolean state = hasParty && data.isEnabled(player.getUUID());
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
