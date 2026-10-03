package brassworks.opac_essentials.claims.purchase;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimPurchasePayload(int amount) implements CustomPacketPayload {
    public static final Type<ClaimPurchasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(opac_essentials.MODID, "purchase_claims")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPurchasePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeVarInt(payload.amount()),
                    buffer -> new ClaimPurchasePayload(buffer.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
