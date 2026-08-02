package brassworks.opac_essentials.listener;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

public final class PartyChatListener {
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        opac_essentials.disablePartyChat(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        opac_essentials.disablePartyChat(event.getEntity().getUUID());
    }
}
