package brassworks.opac_essentials.claims.permission.network;

import brassworks.opac_essentials.claims.menu.ClaimQuickConfigPayload;
import brassworks.opac_essentials.claims.permission.client.ClaimPermissionsClientPayloadHandler;
import brassworks.opac_essentials.claims.permission.server.ClaimPermissionsUiService;
import brassworks.opac_essentials.claims.purchase.ClaimPurchasePayload;
import brassworks.opac_essentials.claims.purchase.ClaimPurchaseService;
import brassworks.opac_essentials.claims.purchase.ClaimPurchaseSettingsPayload;
import brassworks.opac_essentials.claims.purchase.client.ClaimPurchaseClientState;
import brassworks.opac_essentials.party.menu.client.PartyMenuClientPayloadHandler;
import brassworks.opac_essentials.party.menu.network.PartyMenuActionPayload;
import brassworks.opac_essentials.party.menu.network.PartyMenuStatePayload;
import brassworks.opac_essentials.party.menu.server.PartyMenuService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClaimPermissionsNetwork {
    private static final long PURCHASE_INTERVAL_NANOS = 100_000_000L;
    private static final long SETTINGS_INTERVAL_NANOS = 500_000_000L;
    private static final long OPEN_INTERVAL_NANOS = 500_000_000L;
    private static final long QUICK_CONFIG_INTERVAL_NANOS = 150_000_000L;
    private static final Map<UUID, Long> NEXT_PURCHASE_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SETTINGS_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> NEXT_OPEN_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> NEXT_QUICK_CONFIG_REQUEST = new HashMap<>();

    private ClaimPermissionsNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("9").optional();
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
        registrar.playToServer(
                ClaimPurchasePayload.TYPE,
                ClaimPurchasePayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handlePurchase
        );
        registrar.playToClient(
                ClaimPurchaseSettingsPayload.TYPE,
                ClaimPurchaseSettingsPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handlePurchaseSettings
        );
        registrar.playToServer(
                OpenSelectedClaimPermissionsPayload.TYPE,
                OpenSelectedClaimPermissionsPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handleOpenSelected
        );
        registrar.playToServer(
                ClaimQuickConfigPayload.TYPE,
                ClaimQuickConfigPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handleQuickConfig
        );
        registrar.playToClient(
                PartyMenuStatePayload.TYPE,
                PartyMenuStatePayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handlePartyState
        );
        registrar.playToServer(
                PartyMenuActionPayload.TYPE,
                PartyMenuActionPayload.STREAM_CODEC,
                ClaimPermissionsNetwork::handlePartyAction
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
                && player.connection.hasChannel(ClaimPermissionsBatchPayload.TYPE)
                && player.connection.hasChannel(ClaimQuickConfigPayload.TYPE);
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

    public static void sendToServer(ClaimPurchasePayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(OpenSelectedClaimPermissionsPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(ClaimQuickConfigPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(PartyMenuActionPayload payload) {
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

    private static void handlePurchase(ClaimPurchasePayload payload,
                                       IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                Map<UUID, Long> requests = payload.amount() == 0
                        ? NEXT_SETTINGS_REQUEST
                        : NEXT_PURCHASE_REQUEST;
                long interval = payload.amount() == 0
                        ? SETTINGS_INTERVAL_NANOS
                        : PURCHASE_INTERVAL_NANOS;
                if (!allowRequest(
                        requests,
                        player.getUUID(),
                        interval
                )) {
                    return;
                }
                if (payload.amount() == 0) {
                    ClaimPurchaseService.sendSettings(player);
                } else {
                    ClaimPurchaseService.purchase(player, payload.amount());
                }
            });
        }
    }

    private static void handlePurchaseSettings(ClaimPurchaseSettingsPayload payload,
                                               IPayloadContext context) {
        context.enqueueWork(() -> ClaimPurchaseClientState.handle(payload));
    }

    private static void handleOpenSelected(OpenSelectedClaimPermissionsPayload payload,
                                           IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                if (allowRequest(
                        NEXT_OPEN_REQUEST,
                        player.getUUID(),
                        OPEN_INTERVAL_NANOS
                )) {
                    ClaimPermissionsUiService.openSelected(player);
                }
            });
        }
    }

    private static void handleQuickConfig(ClaimQuickConfigPayload payload,
                                          IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                if (allowRequest(
                        NEXT_QUICK_CONFIG_REQUEST,
                        player.getUUID(),
                        QUICK_CONFIG_INTERVAL_NANOS
                )) {
                    ClaimPurchaseService.updateQuickConfig(player, payload);
                }
            });
        }
    }

    private static void handlePartyState(PartyMenuStatePayload payload,
                                         IPayloadContext context) {
        PartyMenuClientPayloadHandler.handle(payload, context);
    }

    private static void handlePartyAction(PartyMenuActionPayload payload,
                                          IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> PartyMenuService.handle(player, payload));
        }
    }

    private static boolean allowRequest(Map<UUID, Long> requests,
                                        UUID playerId,
                                        long interval) {
        long now = System.nanoTime();
        long next = requests.getOrDefault(playerId, 0L);
        if (now < next) {
            return false;
        }
        requests.put(playerId, now + interval);
        if (requests.size() > 1024) {
            requests.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        return true;
    }
}
