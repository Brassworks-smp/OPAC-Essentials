package brassworks.opac_essentials.claims.permission.network;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClaimPermissionsSyncPayload(
        UUID claimOwner,
        int subConfigIndex,
        String claimOwnerName,
        String scopeName,
        boolean adminOverride,
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
                    String claimOwnerName = buffer.readUtf(128);
                    String scopeName = buffer.readUtf(128);
                    boolean adminOverride = buffer.readBoolean();
                    String status = buffer.readUtf(512);
                    boolean error = buffer.readBoolean();
                    int entryCount = checkedSize(buffer.readVarInt(), MAX_ENTRIES, "entry");
                    List<Entry> entries = new ArrayList<>(entryCount);
                    for (int index = 0; index < entryCount; index++) {
                        entries.add(Entry.decode(buffer));
                    }
                    return new ClaimPermissionsSyncPayload(
                            claimOwner,
                            subConfigIndex,
                            claimOwnerName,
                            scopeName,
                            adminOverride,
                            status,
                            error,
                            entries
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   ClaimPermissionsSyncPayload payload) {
                    buffer.writeUUID(payload.claimOwner());
                    buffer.writeVarInt(payload.subConfigIndex());
                    buffer.writeUtf(payload.claimOwnerName(), 128);
                    buffer.writeUtf(payload.scopeName(), 128);
                    buffer.writeBoolean(payload.adminOverride());
                    buffer.writeUtf(payload.status(), 512);
                    buffer.writeBoolean(payload.error());
                    int entryCount = Math.min(payload.entries().size(), MAX_ENTRIES);
                    buffer.writeVarInt(entryCount);
                    for (int index = 0; index < entryCount; index++) {
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

    private static int checkedSize(int size, int maximum, String valueName) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + valueName + " count: " + size);
        }
        return size;
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
