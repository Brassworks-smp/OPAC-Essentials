package org.opnsoc.opac_better_commands.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.parties.command.PartyOnCommandUpdater;
import xaero.pac.common.server.parties.party.IServerParty;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.IPartyPlayerInfo;

import java.util.Objects;

public class PartyMessenger {
    public static void sendPartyMessage(ServerPlayer sender, String text) {
        var server = sender.getServer();
        var serverData = ServerData.from(server);
        assert serverData != null;
        var partyManager = serverData.getPartyManager();
        IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> party = partyManager.getPartyByMember(sender.getUUID());

        if (party == null) {
            sender.sendSystemMessage(
                    Component.literal("You are not in a party!").withStyle(ChatFormatting.RED)
            );
            return;
        }

        IPartyMember senderInfo = party.getMemberInfo(sender.getUUID());
        Component rankComponent = Component.literal((party.getOwner() == senderInfo ? "OWNER" : Objects.requireNonNull(senderInfo).getRank().toString()) + " ").withStyle(s -> s.withColor(senderInfo.getRank().getColor()));
        Component nameComponent = Component.literal("<" + sender.getName().getString() + "> ").withStyle(ChatFormatting.WHITE);
        Component contentComponent = Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.GRAY));
        Component messageComponent = Component.literal("");
        messageComponent.getSiblings().add(rankComponent);
        messageComponent.getSiblings().add(nameComponent);
        messageComponent.getSiblings().add(contentComponent);
        new PartyOnCommandUpdater().update(sender.getUUID(), serverData, party, serverData.getPlayerConfigManager(), mi -> false, messageComponent);
    }
}