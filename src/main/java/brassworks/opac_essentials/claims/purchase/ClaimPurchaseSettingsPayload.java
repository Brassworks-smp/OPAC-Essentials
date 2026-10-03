package brassworks.opac_essentials.claims.purchase;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimPurchaseSettingsPayload(
        String currencyItemId,
        String currencyName,
        int pricePerClaim,
        int currentClaims,
        int claimCount,
        int forceloadCount,
        int forceloadLimit,
        String partyName,
        String partyOwner,
        boolean partyNameEditable,
        int partyMembers,
        int partyAllies,
        int partyInvites,
        int claimColor
) implements CustomPacketPayload {
    public static final Type<ClaimPurchaseSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID,
                    "claim_purchase_settings"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPurchaseSettingsPayload>
            STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUtf(payload.currencyItemId());
                        buffer.writeUtf(payload.currencyName());
                        buffer.writeVarInt(payload.pricePerClaim());
                        buffer.writeVarInt(payload.currentClaims());
                        buffer.writeVarInt(payload.claimCount());
                        buffer.writeVarInt(payload.forceloadCount());
                        buffer.writeVarInt(payload.forceloadLimit());
                        buffer.writeUtf(payload.partyName());
                        buffer.writeUtf(payload.partyOwner());
                        buffer.writeBoolean(payload.partyNameEditable());
                        buffer.writeVarInt(payload.partyMembers());
                        buffer.writeVarInt(payload.partyAllies());
                        buffer.writeVarInt(payload.partyInvites());
                        buffer.writeVarInt(payload.claimColor());
                    },
                    buffer -> new ClaimPurchaseSettingsPayload(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readBoolean(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
