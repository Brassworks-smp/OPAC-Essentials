package brassworks.opac_essentials.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import brassworks.opac_essentials.client.ClaimPermissionsClientPayloadHandler;
import brassworks.opac_essentials.permission.ClaimPermissionsUiService;

public final class ClaimPermissionsNetwork {
    private ClaimPermissionsNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(
                ClaimPermissionsSyncPayload.TYPE,
                ClaimPermissionsSyncPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handleSync
        );
        registrar.playToServer(
                ClaimPermissionMutationPayload.TYPE,
                ClaimPermissionMutationPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handleMutation
        );
    }

    public static void sendTo(ServerPlayer player,
                              ClaimPermissionsSyncPayload payload) {
        if (canUseUi(player)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static boolean canUseUi(ServerPlayer player) {
        return player.connection.hasChannel(ClaimPermissionsSyncPayload.TYPE)
                && player.connection.hasChannel(ClaimPermissionMutationPayload.TYPE);
    }

    private static void handleSync(ClaimPermissionsSyncPayload payload,
                                   IPayloadContext context) {
        ClaimPermissionsClientPayloadHandler.handle(payload, context);
    }

    public static void sendToServer(ClaimPermissionMutationPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleMutation(ClaimPermissionMutationPayload payload,
                                       IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ClaimPermissionsUiService.mutate(player, payload);
        }
    }
}
