package brassworks.opac_essentials.claims.permission.network;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClaimPermissionsBatchPayload(
        Operation operation,
        UUID claimOwner,
        int subConfigIndex,
        boolean enabled,
        List<TargetEntry> targets,
        List<String> actions,
        List<String> players
) implements CustomPacketPayload {
    private static final int MAX_TARGETS = 4096;
    private static final int MAX_ACTIONS = 16;
    private static final int MAX_PLAYERS = 128;

    public static final Type<ClaimPermissionsBatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID, "claim_permissions_batch"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPermissionsBatchPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public ClaimPermissionsBatchPayload decode(RegistryFriendlyByteBuf buffer) {
                    Operation operation = Operation.valueOf(buffer.readUtf(32));
                    UUID claimOwner = buffer.readUUID();
                    int subConfigIndex = buffer.readVarInt();
                    boolean enabled = buffer.readBoolean();

                    int targetCount = checkedSize(buffer.readVarInt(), MAX_TARGETS, "target");
                    List<TargetEntry> targets = new ArrayList<>(targetCount);
                    for (int index = 0; index < targetCount; index++) {
                        targets.add(TargetEntry.decode(buffer));
                    }

                    int actionCount = checkedSize(buffer.readVarInt(), MAX_ACTIONS, "action");
                    List<String> actions = new ArrayList<>(actionCount);
                    for (int index = 0; index < actionCount; index++) {
                        actions.add(buffer.readUtf(32));
                    }

                    int playerCount = checkedSize(buffer.readVarInt(), MAX_PLAYERS, "player");
                    List<String> players = new ArrayList<>(playerCount);
                    for (int index = 0; index < playerCount; index++) {
                        players.add(buffer.readUtf(128));
                    }

                    return new ClaimPermissionsBatchPayload(
                            operation,
                            claimOwner,
                            subConfigIndex,
                            enabled,
                            targets,
                            actions,
                            players
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   ClaimPermissionsBatchPayload payload) {
                    buffer.writeUtf(payload.operation().name(), 32);
                    buffer.writeUUID(payload.claimOwner());
                    buffer.writeVarInt(payload.subConfigIndex());
                    buffer.writeBoolean(payload.enabled());

                    int targetCount = Math.min(payload.targets().size(), MAX_TARGETS);
                    buffer.writeVarInt(targetCount);
                    for (int index = 0; index < targetCount; index++) {
                        payload.targets().get(index).encode(buffer);
                    }

                    int actionCount = Math.min(payload.actions().size(), MAX_ACTIONS);
                    buffer.writeVarInt(actionCount);
                    for (int index = 0; index < actionCount; index++) {
                        buffer.writeUtf(payload.actions().get(index), 32);
                    }

                    int playerCount = Math.min(payload.players().size(), MAX_PLAYERS);
                    buffer.writeVarInt(playerCount);
                    for (int index = 0; index < playerCount; index++) {
                        buffer.writeUtf(payload.players().get(index), 128);
                    }
                }
            };

    public ClaimPermissionsBatchPayload {
        targets = List.copyOf(targets);
        actions = List.copyOf(actions);
        players = List.copyOf(players);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static int checkedSize(int size, int maximum, String valueName) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + valueName + " count: " + size);
        }
        return size;
    }

    public enum Operation {
        BULK_SET,
        APPLY_CHANGES
    }

    public record TargetEntry(String target, String targetId,
                              String playerId, String playerName,
                              String action, boolean enabled) {
        private static TargetEntry decode(RegistryFriendlyByteBuf buffer) {
            return new TargetEntry(
                    buffer.readUtf(32),
                    buffer.readUtf(256),
                    buffer.readUtf(36),
                    buffer.readUtf(128),
                    buffer.readUtf(32),
                    buffer.readBoolean()
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(target, 32);
            buffer.writeUtf(targetId, 256);
            buffer.writeUtf(playerId, 36);
            buffer.writeUtf(playerName, 128);
            buffer.writeUtf(action, 32);
            buffer.writeBoolean(enabled);
        }
    }
}
