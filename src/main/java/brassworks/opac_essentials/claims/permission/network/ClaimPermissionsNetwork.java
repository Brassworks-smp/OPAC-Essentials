package brassworks.opac_essentials.claims.permission.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import brassworks.opac_essentials.claims.permission.client.ClaimPermissionsClientPayloadHandler;
import brassworks.opac_essentials.claims.permission.server.ClaimPermissionsUiService;

public final class ClaimPermissionsNetwork {
    private ClaimPermissionsNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3").optional();
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
        registrar.playToServer(
                ClaimPermissionsBatchPayload.TYPE,
                ClaimPermissionsBatchPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handleBatchMutation
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
                && player.connection.hasChannel(ClaimPermissionMutationPayload.TYPE)
                && player.connection.hasChannel(ClaimPermissionsBatchPayload.TYPE);
    }

    private static void handleSync(ClaimPermissionsSyncPayload payload,
                                   IPayloadContext context) {
        ClaimPermissionsClientPayloadHandler.handle(payload, context);
    }

    public static void sendToServer(ClaimPermissionMutationPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(ClaimPermissionsBatchPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleMutation(ClaimPermissionMutationPayload payload,
                                       IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ClaimPermissionsUiService.mutate(player, payload);
        }
    }

    private static void handleBatchMutation(ClaimPermissionsBatchPayload payload,
                                            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ClaimPermissionsUiService.mutateBatch(player, payload);
        }
    }
}
