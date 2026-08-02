package brassworks.opac_essentials.utils;

import net.minecraft.server.level.ServerPlayer;

public final class PartyMessenger {
    private PartyMessenger() {
    }

    public static void sendPartyMessage(ServerPlayer sender, String text) {
        sender.getServer().getCommands().performPrefixedCommand(
                sender.createCommandSourceStack(),
                "opm " + text
        );
    }
}
