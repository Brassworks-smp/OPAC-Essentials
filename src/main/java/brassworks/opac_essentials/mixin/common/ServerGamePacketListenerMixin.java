package brassworks.opac_essentials.mixin.common;

import brassworks.opac_essentials.partychat.PartyChatSavedData;
import brassworks.opac_essentials.partychat.PartyMessenger;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.pac.common.server.api.OpenPACServerAPI;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2000)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$redirectPartyChat(PlayerChatMessage message,
                                                  CallbackInfo callback) {
        PartyChatSavedData data = PartyChatSavedData.get(player.server);
        if (!data.isEnabled(player.getUUID())) {
            return;
        }
        if (OpenPACServerAPI.get(player.server).getPartyManager()
                .getPartyByMember(player.getUUID()) == null) {
            data.setEnabled(player.getUUID(), false);
            return;
        }
        PartyMessenger.sendPartyMessage(player, message.signedContent());
        callback.cancel();
    }
}
