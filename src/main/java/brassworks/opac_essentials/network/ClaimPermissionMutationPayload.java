package brassworks.opac_essentials.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import brassworks.opac_essentials.opac_essentials;

import java.util.UUID;

public record ClaimPermissionMutationPayload(
        Operation operation,
        UUID claimOwner,
        int subConfigIndex,
        String target,
        String targetId,
        String action,
        String replacementAction,
        String playerId,
        String playerName
) implements CustomPacketPayload {
    public static final Type<ClaimPermissionMutationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID, "claim_permission_mutation"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPermissionMutationPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public ClaimPermissionMutationPayload decode(RegistryFriendlyByteBuf buffer) {
                    int operationId = buffer.readVarInt();
                    Operation[] operations = Operation.values();
                    if (operationId < 0 || operationId >= operations.length) {
                        throw new IllegalArgumentException(
                                "Unknown claim permission operation: " + operationId
                        );
                    }
                    Operation operation = operations[operationId];
                    return new ClaimPermissionMutationPayload(
                            operation,
                            buffer.readUUID(),
                            buffer.readVarInt(),
                            buffer.readUtf(32),
                            buffer.readUtf(256),
                            buffer.readUtf(32),
                            buffer.readUtf(32),
                            buffer.readUtf(36),
                            buffer.readUtf(128)
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   ClaimPermissionMutationPayload payload) {
                    buffer.writeVarInt(payload.operation().ordinal());
                    buffer.writeUUID(payload.claimOwner());
                    buffer.writeVarInt(payload.subConfigIndex());
                    buffer.writeUtf(payload.target(), 32);
                    buffer.writeUtf(payload.targetId(), 256);
                    buffer.writeUtf(payload.action(), 32);
                    buffer.writeUtf(payload.replacementAction(), 32);
                    buffer.writeUtf(payload.playerId(), 36);
                    buffer.writeUtf(payload.playerName(), 128);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Operation {
        ADD,
        REMOVE,
        CHANGE_ACTION
    }
}
