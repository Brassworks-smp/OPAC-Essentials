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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimPermissionsSavedData extends SavedData {
    public static final int MAX_PERMISSIONS_PER_OWNER = 4096;

    private static final String DATA_NAME = "opac_better_commands_claim_permissions";
    private static final String PERMISSIONS_TAG = "permissions";
    private static final String TRUSTED_PLAYERS_TAG = "trustedPlayers";
    private static final String LEGACY_GROUPS_TAG = "groups";
    private static final int GLOBAL_SUB_CONFIG_INDEX = -1;

    private final Set<ClaimPermissionKey> permissions = new HashSet<>();
    private final Set<ClaimTrustKey> trustedPlayers = new HashSet<>();
    private final Map<UUID, Integer> permissionCounts = new HashMap<>();

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
                    data.addLoaded(normalizePermission(new ClaimPermissionKey(
                            owner, subConfigIndex, target, targetId, action, player
                    )));
                }
            } catch (RuntimeException ignored) {
            }
        }

        ListTag trustedPlayerList = tag.getList(TRUSTED_PLAYERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < trustedPlayerList.size(); i++) {
            CompoundTag entry = trustedPlayerList.getCompound(i);
            try {
                data.trustedPlayers.add(new ClaimTrustKey(
                        entry.getUUID("owner"),
                        entry.getInt("subConfigIndex"),
                        entry.getUUID("player")
                ));
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
        permission = normalizePermission(permission);
        if (permission.player() != null && permissions.contains(new ClaimPermissionKey(
                permission.claimOwner(),
                permission.subConfigIndex(),
                permission.target(),
                permission.targetId(),
                permission.action(),
                null
        ))) {
            return false;
        }
        if (permissions.contains(permission)
                || isAtCapacity(permission.claimOwner())) {
            return false;
        }
        boolean changed = permissions.add(permission);
        if (changed) {
            permissionCounts.merge(permission.claimOwner(), 1, Integer::sum);
            setDirty();
        }
        return changed;
    }

    public boolean remove(ClaimPermissionKey permission) {
        permission = normalizePermission(permission);
        boolean changed = permissions.remove(permission);
        if (changed) {
            permissionCounts.computeIfPresent(
                    permission.claimOwner(),
                    (owner, count) -> count > 1 ? count - 1 : null
            );
            setDirty();
        }
        return changed;
    }

    public boolean contains(ClaimPermissionKey permission) {
        return permissions.contains(normalizePermission(permission));
    }

    public boolean isAtCapacity(UUID owner) {
        return permissionCounts.getOrDefault(owner, 0)
                >= MAX_PERMISSIONS_PER_OWNER;
    }

    public int remainingCapacity(UUID owner) {
        return Math.max(
                0,
                MAX_PERMISSIONS_PER_OWNER
                        - permissionCounts.getOrDefault(owner, 0)
        );
    }

    public boolean allows(UUID owner, int subConfigIndex, ClaimPermissionTarget target,
                          ResourceLocation targetId, ClaimPermissionAction action, UUID player) {
        subConfigIndex = normalizedSubConfigIndex(subConfigIndex, target, action);
        return permissions.contains(new ClaimPermissionKey(
                owner, subConfigIndex, target, targetId, action, null
        )) || permissions.contains(new ClaimPermissionKey(
                owner, subConfigIndex, target, targetId, action, player
        ));
    }

    public boolean trust(UUID owner, int subConfigIndex, UUID player) {
        boolean changed = trustedPlayers.add(new ClaimTrustKey(
                owner, subConfigIndex, player
        ));
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean untrust(UUID owner, int subConfigIndex, UUID player) {
        boolean changed = trustedPlayers.remove(new ClaimTrustKey(
                owner, subConfigIndex, player
        ));
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean isTrusted(UUID owner, int subConfigIndex, UUID player) {
        return trustedPlayers.contains(new ClaimTrustKey(
                owner, subConfigIndex, player
        ));
    }

    public List<UUID> listTrusted(UUID owner, int subConfigIndex) {
        return trustedPlayers.stream()
                .filter(entry -> entry.claimOwner().equals(owner)
                        && entry.subConfigIndex() == subConfigIndex)
                .map(ClaimTrustKey::player)
                .sorted()
                .toList();
    }

    public List<ClaimPermissionKey> list(UUID owner, int subConfigIndex) {
        List<ClaimPermissionKey> result = new ArrayList<>();
        for (ClaimPermissionKey permission : permissions) {
            if (permission.claimOwner().equals(owner)
                    && (permission.subConfigIndex() == subConfigIndex
                        || isGlobalPermission(permission))) {
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
        ListTag trustedPlayerList = new ListTag();
        for (ClaimTrustKey trustedPlayer : trustedPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", trustedPlayer.claimOwner());
            entry.putInt("subConfigIndex", trustedPlayer.subConfigIndex());
            entry.putUUID("player", trustedPlayer.player());
            trustedPlayerList.add(entry);
        }
        tag.put(TRUSTED_PLAYERS_TAG, trustedPlayerList);
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
                        migrated |= data.addLoaded(normalizePermission(new ClaimPermissionKey(
                                owner,
                                subConfigIndex,
                                rule.target(),
                                rule.targetId(),
                                rule.action(),
                                member
                        )));
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

    private boolean addLoaded(ClaimPermissionKey permission) {
        if (permissions.add(permission)) {
            permissionCounts.merge(permission.claimOwner(), 1, Integer::sum);
            return true;
        }
        return false;
    }

    private static boolean validRule(ClaimPermissionTarget target,
                                     ResourceLocation targetId,
                                     ClaimPermissionAction action) {
        return targetId != null && target.supports(action) && target.isRegistered(targetId);
    }

    private static ClaimPermissionKey normalizePermission(ClaimPermissionKey permission) {
        int subConfigIndex = normalizedSubConfigIndex(
                permission.subConfigIndex(),
                permission.target(),
                permission.action()
        );
        if (subConfigIndex == permission.subConfigIndex()) {
            return permission;
        }
        return new ClaimPermissionKey(
                permission.claimOwner(),
                subConfigIndex,
                permission.target(),
                permission.targetId(),
                permission.action(),
                permission.player()
        );
    }

    private static int normalizedSubConfigIndex(int subConfigIndex,
                                                ClaimPermissionTarget target,
                                                ClaimPermissionAction action) {
        return target == ClaimPermissionTarget.TRAIN
                && (action == ClaimPermissionAction.INTERACT
                    || action == ClaimPermissionAction.CONTROL)
                ? GLOBAL_SUB_CONFIG_INDEX
                : subConfigIndex;
    }

    private static boolean isGlobalPermission(ClaimPermissionKey permission) {
        return permission.subConfigIndex() == GLOBAL_SUB_CONFIG_INDEX
                && permission.target() == ClaimPermissionTarget.TRAIN
                && (permission.action() == ClaimPermissionAction.INTERACT
                    || permission.action() == ClaimPermissionAction.CONTROL);
    }

    private record LegacyRule(ClaimPermissionTarget target, ResourceLocation targetId,
                              ClaimPermissionAction action) {
    }

    private record ClaimTrustKey(UUID claimOwner, int subConfigIndex, UUID player) {
    }
}
