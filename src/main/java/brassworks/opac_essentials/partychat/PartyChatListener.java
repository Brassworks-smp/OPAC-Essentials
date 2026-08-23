package brassworks.opac_essentials.partychat;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

public final class PartyChatListener {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerChat(ServerChatEvent event) {
        if (!opac_essentials.isPartyChatEnabled(event.getPlayer().getUUID())) {
            return;
        }
        PartyMessenger.sendPartyMessage(event.getPlayer(), event.getRawText());
        event.setCanceled(true);
    }

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
