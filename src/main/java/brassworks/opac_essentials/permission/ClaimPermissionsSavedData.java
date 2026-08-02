package brassworks.opac_essentials.permission;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ClaimPermissionsSavedData extends SavedData {
    private static final String DATA_NAME = "opac_better_commands_claim_permissions";
    private static final String PERMISSIONS_TAG = "permissions";

    private final Set<ClaimPermissionKey> permissions = new HashSet<>();

    public static ClaimPermissionsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ClaimPermissionsSavedData::new, ClaimPermissionsSavedData::load),
                DATA_NAME
        );
    }

    public static ClaimPermissionsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClaimPermissionsSavedData data = new ClaimPermissionsSavedData();
        ListTag list = tag.getList(PERMISSIONS_TAG, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID owner = entry.getUUID("owner");
                int subConfigIndex = entry.getInt("subConfigIndex");
                ClaimPermissionTarget target =
                        ClaimPermissionTarget.valueOf(entry.getString("target"));
                ClaimPermissionAction action =
                        ClaimPermissionAction.valueOf(entry.getString("action"));
                if (target == ClaimPermissionTarget.ENTITY
                        && action == ClaimPermissionAction.BREAK) {
                    action = ClaimPermissionAction.ATTACK;
                }
                ResourceLocation targetId =
                        ResourceLocation.tryParse(entry.getString("targetId"));
                UUID player = entry.hasUUID("player") ? entry.getUUID("player") : null;

                if (targetId != null && target.supports(action) && target.isRegistered(targetId)) {
                    data.permissions.add(new ClaimPermissionKey(
                            owner, subConfigIndex, target, targetId, action, player
                    ));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return data;
    }

    public boolean add(ClaimPermissionKey permission) {
        boolean changed = permissions.add(permission);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean remove(ClaimPermissionKey permission) {
        boolean changed = permissions.remove(permission);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean allows(UUID owner, int subConfigIndex, ClaimPermissionTarget target,
                          ResourceLocation targetId, ClaimPermissionAction action, UUID player) {
        return permissions.contains(new ClaimPermissionKey(
                owner, subConfigIndex, target, targetId, action, null
        )) || permissions.contains(new ClaimPermissionKey(
                owner, subConfigIndex, target, targetId, action, player
        ));
    }

    public List<ClaimPermissionKey> list(UUID owner, int subConfigIndex) {
        List<ClaimPermissionKey> result = new ArrayList<>();
        for (ClaimPermissionKey permission : permissions) {
            if (permission.claimOwner().equals(owner)
                    && permission.subConfigIndex() == subConfigIndex) {
                result.add(permission);
            }
        }
        result.sort(Comparator
                .comparing((ClaimPermissionKey permission) -> permission.target().commandName())
                .thenComparing(permission -> permission.targetId().toString())
                .thenComparing(permission -> permission.action().commandName())
                .thenComparing(permission ->
                        permission.player() == null ? "" : permission.player().toString()));
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ClaimPermissionKey permission : permissions) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", permission.claimOwner());
            entry.putInt("subConfigIndex", permission.subConfigIndex());
            entry.putString("target", permission.target().name());
            entry.putString("targetId", permission.targetId().toString());
            entry.putString("action", permission.action().name());
            if (permission.player() != null) {
                entry.putUUID("player", permission.player());
            }
            list.add(entry);
        }
        tag.put(PERMISSIONS_TAG, list);
        return tag;
    }
}
