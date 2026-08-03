package brassworks.opac_essentials.claims.permission.server;

import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction;
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionKey;
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget;
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
    private static final String LEGACY_GROUPS_TAG = "groups";

    private final Set<ClaimPermissionKey> permissions = new HashSet<>();

    public static ClaimPermissionsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ClaimPermissionsSavedData::new, ClaimPermissionsSavedData::load),
                DATA_NAME
        );
    }

    public static ClaimPermissionsSavedData load(CompoundTag tag,
                                                  HolderLookup.Provider registries) {
        ClaimPermissionsSavedData data = new ClaimPermissionsSavedData();
        ListTag permissionList = tag.getList(PERMISSIONS_TAG, Tag.TAG_COMPOUND);

        for (int i = 0; i < permissionList.size(); i++) {
            CompoundTag entry = permissionList.getCompound(i);
            try {
                UUID owner = entry.getUUID("owner");
                int subConfigIndex = entry.getInt("subConfigIndex");
                ClaimPermissionTarget target =
                        ClaimPermissionTarget.valueOf(entry.getString("target"));
                ClaimPermissionAction action = normalizedAction(
                        target,
                        ClaimPermissionAction.valueOf(entry.getString("action"))
                );
                ResourceLocation targetId =
                        ResourceLocation.tryParse(entry.getString("targetId"));
                UUID player = entry.hasUUID("player") ? entry.getUUID("player") : null;

                if (validRule(target, targetId, action)) {
                    data.permissions.add(new ClaimPermissionKey(
                            owner, subConfigIndex, target, targetId, action, player
                    ));
                }
            } catch (RuntimeException ignored) {
            }
        }

        boolean migrated = migrateLegacyGroups(
                data,
                tag.getList(LEGACY_GROUPS_TAG, Tag.TAG_COMPOUND)
        );
        if (migrated) {
            data.setDirty();
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
        ListTag permissionList = new ListTag();
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
            permissionList.add(entry);
        }
        tag.put(PERMISSIONS_TAG, permissionList);
        tag.remove(LEGACY_GROUPS_TAG);
        return tag;
    }

    private static boolean migrateLegacyGroups(ClaimPermissionsSavedData data,
                                               ListTag groupList) {
        boolean migrated = false;
        for (int i = 0; i < groupList.size(); i++) {
            CompoundTag entry = groupList.getCompound(i);
            try {
                UUID owner = entry.getUUID("owner");
                int subConfigIndex = entry.getInt("subConfigIndex");
                List<UUID> members = readLegacyMembers(entry);
                List<LegacyRule> rules = readLegacyRules(entry);
                for (UUID member : members) {
                    for (LegacyRule rule : rules) {
                        migrated |= data.permissions.add(new ClaimPermissionKey(
                                owner,
                                subConfigIndex,
                                rule.target(),
                                rule.targetId(),
                                rule.action(),
                                member
                        ));
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return migrated || !groupList.isEmpty();
    }

    private static List<UUID> readLegacyMembers(CompoundTag entry) {
        List<UUID> members = new ArrayList<>();
        ListTag memberList = entry.getList("members", Tag.TAG_COMPOUND);
        for (int index = 0; index < memberList.size(); index++) {
            CompoundTag member = memberList.getCompound(index);
            if (member.hasUUID("id")) {
                members.add(member.getUUID("id"));
            }
        }
        return members;
    }

    private static List<LegacyRule> readLegacyRules(CompoundTag entry) {
        List<LegacyRule> rules = new ArrayList<>();
        ListTag ruleList = entry.getList("rules", Tag.TAG_COMPOUND);
        for (int index = 0; index < ruleList.size(); index++) {
            CompoundTag rule = ruleList.getCompound(index);
            try {
                ClaimPermissionTarget target =
                        ClaimPermissionTarget.valueOf(rule.getString("target"));
                ClaimPermissionAction action = normalizedAction(
                        target,
                        ClaimPermissionAction.valueOf(rule.getString("action"))
                );
                ResourceLocation targetId =
                        ResourceLocation.tryParse(rule.getString("targetId"));
                if (validRule(target, targetId, action)) {
                    rules.add(new LegacyRule(target, targetId, action));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return rules;
    }

    private static ClaimPermissionAction normalizedAction(ClaimPermissionTarget target,
                                                          ClaimPermissionAction action) {
        return target == ClaimPermissionTarget.ENTITY
                && action == ClaimPermissionAction.BREAK
                ? ClaimPermissionAction.ATTACK
                : action;
    }

    private static boolean validRule(ClaimPermissionTarget target,
                                     ResourceLocation targetId,
                                     ClaimPermissionAction action) {
        return targetId != null && target.supports(action) && target.isRegistered(targetId);
    }

    private record LegacyRule(ClaimPermissionTarget target, ResourceLocation targetId,
                              ClaimPermissionAction action) {
    }
}
