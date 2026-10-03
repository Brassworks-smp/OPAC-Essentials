package brassworks.opac_essentials.claims.permission.network;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenSelectedClaimPermissionsPayload() implements CustomPacketPayload {
    public static final Type<OpenSelectedClaimPermissionsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID,
                    "open_selected_claim_permissions"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSelectedClaimPermissionsPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                    },
                    buffer -> new OpenSelectedClaimPermissionsPayload()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
