package brassworks.opac_essentials.party.menu.network;

import brassworks.opac_essentials.opac_essentials;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PartyMenuStatePayload(
        boolean hasParty,
        boolean openScreen,
        String partyName,
        String ownerName,
        boolean owner,
        String selfRank,
        boolean canManageMembers,
        boolean canManageRanks,
        boolean chatEnabled,
        int allyCount,
        List<MemberEntry> members,
        List<PlayerEntry> players
) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 512;

    public static final Type<PartyMenuStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    opac_essentials.MODID,
                    "party_menu_state"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyMenuStatePayload>
            STREAM_CODEC = StreamCodec.of(
                    PartyMenuStatePayload::encode,
                    PartyMenuStatePayload::decode
            );

    public PartyMenuStatePayload {
        members = List.copyOf(members);
        players = List.copyOf(players);
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               PartyMenuStatePayload payload) {
        buffer.writeBoolean(payload.hasParty());
        buffer.writeBoolean(payload.openScreen());
        buffer.writeUtf(payload.partyName(), 100);
        buffer.writeUtf(payload.ownerName(), 64);
        buffer.writeBoolean(payload.owner());
        buffer.writeUtf(payload.selfRank(), 32);
        buffer.writeBoolean(payload.canManageMembers());
        buffer.writeBoolean(payload.canManageRanks());
        buffer.writeBoolean(payload.chatEnabled());
        buffer.writeVarInt(payload.allyCount());
        buffer.writeVarInt(payload.members().size());
        for (MemberEntry member : payload.members()) {
            buffer.writeUUID(member.id());
            buffer.writeUtf(member.name(), 64);
            buffer.writeUtf(member.rank(), 32);
            buffer.writeBoolean(member.owner());
            buffer.writeBoolean(member.online());
        }
        buffer.writeVarInt(payload.players().size());
        for (PlayerEntry player : payload.players()) {
            buffer.writeUUID(player.id());
            buffer.writeUtf(player.name(), 64);
            buffer.writeBoolean(player.invited());
            buffer.writeBoolean(player.online());
        }
    }

    private static PartyMenuStatePayload decode(RegistryFriendlyByteBuf buffer) {
        boolean hasParty = buffer.readBoolean();
        boolean openScreen = buffer.readBoolean();
        String partyName = buffer.readUtf(100);
        String ownerName = buffer.readUtf(64);
        boolean owner = buffer.readBoolean();
        String selfRank = buffer.readUtf(32);
        boolean canManageMembers = buffer.readBoolean();
        boolean canManageRanks = buffer.readBoolean();
        boolean chatEnabled = buffer.readBoolean();
        int allyCount = buffer.readVarInt();
        int memberCount = boundedCount(buffer, "member");
        List<MemberEntry> members = new ArrayList<>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            members.add(new MemberEntry(
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readUtf(32),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }
        int playerCount = boundedCount(buffer, "player");
        List<PlayerEntry> players = new ArrayList<>(playerCount);
        for (int index = 0; index < playerCount; index++) {
            players.add(new PlayerEntry(
                    buffer.readUUID(),
                    buffer.readUtf(64),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }
        return new PartyMenuStatePayload(
                hasParty,
                openScreen,
                partyName,
                ownerName,
                owner,
                selfRank,
                canManageMembers,
                canManageRanks,
                chatEnabled,
                allyCount,
                members,
                players
        );
    }

    private static int boundedCount(RegistryFriendlyByteBuf buffer,
                                    String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new DecoderException(
                    "Invalid party menu " + name + " count: " + count
            );
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record MemberEntry(
            UUID id,
            String name,
            String rank,
            boolean owner,
            boolean online
    ) {
    }

    public record PlayerEntry(
            UUID id,
            String name,
            boolean invited,
            boolean online
    ) {
    }
}
