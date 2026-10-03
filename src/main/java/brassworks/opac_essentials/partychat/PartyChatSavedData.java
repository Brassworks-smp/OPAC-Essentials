package brassworks.opac_essentials.partychat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PartyChatSavedData extends SavedData {
    private static final String DATA_NAME = "opac_essentials_party_chat";
    private static final String ENABLED_PLAYERS_TAG = "enabledPlayers";

    private final Set<UUID> enabledPlayers = new HashSet<>();

    public static PartyChatSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PartyChatSavedData::new, PartyChatSavedData::load),
                DATA_NAME
        );
    }

    public static PartyChatSavedData load(CompoundTag tag,
                                          HolderLookup.Provider registries) {
        PartyChatSavedData data = new PartyChatSavedData();
        ListTag players = tag.getList(ENABLED_PLAYERS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (entry.hasUUID("player")) {
                data.enabledPlayers.add(entry.getUUID("player"));
            }
        }
        return data;
    }

    public boolean toggle(UUID playerId) {
        boolean enabled;
        if (enabledPlayers.remove(playerId)) {
            enabled = false;
        } else {
            enabledPlayers.add(playerId);
            enabled = true;
        }
        setDirty();
        return enabled;
    }

    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        boolean changed = enabled
                ? enabledPlayers.add(playerId)
                : enabledPlayers.remove(playerId);
        if (changed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (UUID playerId : enabledPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", playerId);
            players.add(entry);
        }
        tag.put(ENABLED_PLAYERS_TAG, players);
        return tag;
    }
}
