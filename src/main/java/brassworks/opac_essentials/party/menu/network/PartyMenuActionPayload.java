package brassworks.opac_essentials.party.menu.network;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record PartyMenuActionPayload(
        Action action,
        UUID target,
        String value
) implements CustomPacketPayload {
    public static final Type<PartyMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID,
                    "party_menu_action"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyMenuActionPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public PartyMenuActionPayload decode(RegistryFriendlyByteBuf buffer) {
                    int actionId = buffer.readVarInt();
                    Action[] actions = Action.values();
                    if (actionId < 0 || actionId >= actions.length) {
                        throw new IllegalArgumentException(
                                "Unknown party menu action: " + actionId
                        );
                    }
                    UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
                    return new PartyMenuActionPayload(
                            actions[actionId],
                            target,
                            buffer.readUtf(100)
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   PartyMenuActionPayload payload) {
                    buffer.writeVarInt(payload.action().ordinal());
                    buffer.writeBoolean(payload.target() != null);
                    if (payload.target() != null) {
                        buffer.writeUUID(payload.target());
                    }
                    buffer.writeUtf(payload.value(), 100);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        OPEN,
        REFRESH,
        CREATE,
        RENAME,
        TOGGLE_CHAT,
        INVITE,
        CANCEL_INVITE,
        KICK,
        PROMOTE,
        DEMOTE,
        TRANSFER,
        LEAVE,
        DISBAND
    }
}
