package brassworks.opac_essentials.claims.menu;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimQuickConfigPayload(
        Operation operation,
        String partyName,
        int claimColor
) implements CustomPacketPayload {
    public static final Type<ClaimQuickConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID,
                    "claim_quick_config"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimQuickConfigPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public ClaimQuickConfigPayload decode(RegistryFriendlyByteBuf buffer) {
                    int operationId = buffer.readVarInt();
                    Operation[] operations = Operation.values();
                    if (operationId < 0 || operationId >= operations.length) {
                        throw new IllegalArgumentException(
                                "Unknown claim quick config operation: " + operationId
                        );
                    }
                    return new ClaimQuickConfigPayload(
                            operations[operationId],
                            buffer.readUtf(100),
                            buffer.readVarInt()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   ClaimQuickConfigPayload payload) {
                    buffer.writeVarInt(payload.operation().ordinal());
                    buffer.writeUtf(payload.partyName(), 100);
                    buffer.writeVarInt(payload.claimColor());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Operation {
        PARTY_NAME,
        CLAIM_COLOR
    }
}
