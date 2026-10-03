package brassworks.opac_essentials.partychat;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class PartyChatListener {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerChat(ServerChatEvent event) {
        PartyChatSavedData data = PartyChatSavedData.get(
                event.getPlayer().server
        );
        if (!data.isEnabled(event.getPlayer().getUUID())) {
            return;
        }
        if (OpenPACServerAPI.get(event.getPlayer().server).getPartyManager()
                .getPartyByMember(event.getPlayer().getUUID()) == null) {
            data.setEnabled(event.getPlayer().getUUID(), false);
            return;
        }
        PartyMessenger.sendPartyMessage(event.getPlayer(), event.getRawText());
        event.setCanceled(true);
    }
}
