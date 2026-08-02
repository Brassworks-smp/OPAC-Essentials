package brassworks.opac_essentials.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import brassworks.opac_essentials.opac_essentials;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClaimPermissionsSyncPayload(
        UUID claimOwner,
        int subConfigIndex,
        String scopeName,
        String status,
        boolean error,
        List<Entry> entries
) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 4096;

    public static final Type<ClaimPermissionsSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID, "claim_permissions_sync"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimPermissionsSyncPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public ClaimPermissionsSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    UUID claimOwner = buffer.readUUID();
                    int subConfigIndex = buffer.readVarInt();
                    String scopeName = buffer.readUtf(128);
                    String status = buffer.readUtf(512);
                    boolean error = buffer.readBoolean();
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_ENTRIES) {
                        throw new IllegalArgumentException(
                                "Invalid permission entry count: " + size
                        );
                    }
                    List<Entry> entries = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        entries.add(Entry.decode(buffer));
                    }
                    return new ClaimPermissionsSyncPayload(
                            claimOwner,
                            subConfigIndex,
                            scopeName,
                            status,
                            error,
                            List.copyOf(entries)
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   ClaimPermissionsSyncPayload payload) {
                    buffer.writeUUID(payload.claimOwner());
                    buffer.writeVarInt(payload.subConfigIndex());
                    buffer.writeUtf(payload.scopeName(), 128);
                    buffer.writeUtf(payload.status(), 512);
                    buffer.writeBoolean(payload.error());
                    int size = Math.min(payload.entries().size(), MAX_ENTRIES);
                    buffer.writeVarInt(size);
                    for (int index = 0; index < size; index++) {
                        payload.entries().get(index).encode(buffer);
                    }
                }
            };

    public ClaimPermissionsSyncPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String target, String targetId, String action,
                        String playerId, String playerName) {
        private static Entry decode(RegistryFriendlyByteBuf buffer) {
            return new Entry(
                    buffer.readUtf(32),
                    buffer.readUtf(256),
                    buffer.readUtf(32),
                    buffer.readUtf(36),
                    buffer.readUtf(128)
            );
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(target, 32);
            buffer.writeUtf(targetId, 256);
            buffer.writeUtf(action, 32);
            buffer.writeUtf(playerId, 36);
            buffer.writeUtf(playerName, 128);
        }
    }
}
