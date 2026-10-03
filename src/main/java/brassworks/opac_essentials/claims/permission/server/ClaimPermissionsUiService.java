package brassworks.opac_essentials.claims.permission.server;

import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction;
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionKey;
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionMutationPayload;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsBatchPayload;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload;
import brassworks.opac_essentials.compat.openpac.OpenPacCompat;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimPermissionsUiService {
    private static final UUID INVALID_PLAYER = new UUID(0L, 0L);
    private static final int MAX_BATCH_COMBINATIONS = 4096;
    private static final long MUTATION_INTERVAL_NANOS = 100_000_000L;
    private static final Map<UUID, Long> NEXT_MUTATION = new HashMap<>();

    private ClaimPermissionsUiService() {
    }

    public static int open(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "This command can only be used by a player."
            ));
            return 0;
        }

        OpenPacCompat.Claim claim = getEditableClaim(player, true);
        if (claim == null) {
            return 0;
        }
        sync(player, claim, "", false);
        return 1;
    }

    public static void openSelected(ServerPlayer player) {
        open(player.createCommandSourceStack());
    }

    public static void mutate(ServerPlayer player,
                              ClaimPermissionMutationPayload payload) {
        if (!allowMutation(player)) {
            rejectMutation(player, payload.claimOwner(), payload.subConfigIndex(),
                    "Please wait before changing permissions again.");
            return;
        }
        OpenPacCompat.Claim claim = currentPayloadClaim(
                player, payload.claimOwner(), payload.subConfigIndex()
        );
        if (claim == null) {
            return;
        }

        ClaimPermissionTarget target;
        List<ClaimPermissionAction> actions;
        ResourceLocation targetId = ResourceLocation.tryParse(payload.targetId());
        try {
            target = ClaimPermissionTarget.valueOf(payload.target());
            actions = parseActions(payload.action());
        } catch (IllegalArgumentException exception) {
            sync(player, claim, "Unknown category or action.", true);
            return;
        }

        if (actions.isEmpty()
                || (payload.operation() != ClaimPermissionMutationPayload.Operation.ADD
                && actions.size() != 1)) {
            sync(player, claim, "Unknown category or action.", true);
            return;
        }

        if (targetId == null || !target.isRegistered(targetId)) {
            sync(player, claim, "Unknown registry ID: " + payload.targetId(), true);
            return;
        }
        if (actions.stream().anyMatch(action -> !target.supports(action))) {
            sync(player, claim, "That action is not valid for this category.", true);
            return;
        }

        UUID selectedPlayer = resolvePlayer(player, payload);
        if (INVALID_PLAYER.equals(selectedPlayer)) {
            sync(player, claim,
                    "Player not found. Use 'all' or a known player name.",
                    true);
            return;
        }

        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(player.getServer());
        ClaimPermissionAction action = actions.getFirst();
        ClaimPermissionKey key = key(claim, target, targetId, action, selectedPlayer);
        String status;
        boolean error = false;

        switch (payload.operation()) {
            case ADD -> {
                long additions = actions.stream()
                        .map(selectedAction -> key(
                                claim,
                                target,
                                targetId,
                                selectedAction,
                                selectedPlayer
                        ))
                        .filter(permission -> !data.contains(permission))
                        .count();
                if (additions > data.remainingCapacity(claim.ownerId())) {
                    sync(player, claim,
                            "The permission limit for this claim owner would be exceeded.",
                            true);
                    return;
                }
                int added = 0;
                for (ClaimPermissionAction selectedAction : actions) {
                    if (data.add(key(
                            claim,
                            target,
                            targetId,
                            selectedAction,
                            selectedPlayer
                    ))) {
                        added++;
                    }
                }
                int existing = actions.size() - added;
                if (added == 0) {
                    status = actions.size() == 1
                            ? "Nothing changed. That permission already exists."
                            : "Nothing changed. Those permissions already exist.";
                } else if (actions.size() == 1) {
                    status = "Permission added.";
                } else if (existing == 0) {
                    status = added + " permissions added.";
                } else {
                    status = added + " permissions added; "
                            + existing + " already existed.";
                }
            }
            case REMOVE -> {
                if (!data.remove(key)) {
                    status = "That permission no longer exists.";
                    error = true;
                } else {
                    status = "Permission removed.";
                }
            }
            case CHANGE_ACTION -> {
                ClaimPermissionAction replacement;
                try {
                    replacement = ClaimPermissionAction.valueOf(
                            payload.replacementAction()
                    );
                } catch (IllegalArgumentException exception) {
                    sync(player, claim, "Unknown replacement action.", true);
                    return;
                }
                if (!target.supports(replacement)) {
                    sync(player, claim,
                            "That action is not valid for this category.", true);
                    return;
                }
                ClaimPermissionKey replacementKey = key(
                        claim, target, targetId, replacement, selectedPlayer
                );
                if (!data.remove(key)) {
                    status = "That permission no longer exists.";
                    error = true;
                } else if (!data.add(replacementKey)) {
                    data.add(key);
                    status = "A permission with that action already exists.";
                    error = true;
                } else {
                    status = "Action changed.";
                }
            }
            default -> throw new IllegalStateException("Unexpected operation");
        }

        sync(player, claim, status, error);
    }

    public static void mutateBatch(ServerPlayer player,
                                   ClaimPermissionsBatchPayload payload) {
        if (!allowMutation(player)) {
            rejectMutation(player, payload.claimOwner(), payload.subConfigIndex(),
                    "Please wait before changing permissions again.");
            return;
        }
        OpenPacCompat.Claim claim = currentPayloadClaim(
                player, payload.claimOwner(), payload.subConfigIndex()
        );
        if (claim == null) {
            return;
        }

        switch (payload.operation()) {
            case BULK_SET -> mutateBulk(player, claim, payload);
            case APPLY_CHANGES -> applyChanges(player, claim, payload);
        }
    }

    private static void mutateBulk(ServerPlayer player, OpenPacCompat.Claim claim,
                                   ClaimPermissionsBatchPayload payload) {
        Set<ClaimPermissionAction> actions = parseActionSet(payload.actions());
        PlayerSelection playerSelection = resolveSelectedPlayers(player, payload.players());
        if (playerSelection.error() != null) {
            sync(player, claim, playerSelection.error(), true);
            return;
        }
        if (payload.targets().isEmpty() || actions.isEmpty()
                || playerSelection.players().isEmpty()) {
            sync(player, claim, "Select targets, actions and players first.", true);
            return;
        }

        Set<ParsedTarget> targets = new LinkedHashSet<>();
        for (ClaimPermissionsBatchPayload.TargetEntry targetEntry : payload.targets()) {
            ParsedTarget parsed = parseTarget(targetEntry);
            if (parsed != null) {
                targets.add(parsed);
            }
        }

        Set<ClaimPermissionKey> permissions = new LinkedHashSet<>();
        for (ParsedTarget parsed : targets) {
            for (ClaimPermissionAction action : actions) {
                if (!parsed.target().supports(action)) {
                    continue;
                }
                for (UUID selectedPlayer : playerSelection.players()) {
                    permissions.add(key(
                            claim,
                            parsed.target(),
                            parsed.targetId(),
                            action,
                            selectedPlayer
                    ));
                    if (permissions.size() > MAX_BATCH_COMBINATIONS) {
                        sync(player, claim,
                                "Too many permission combinations were selected.",
                                true);
                        return;
                    }
                }
            }
        }

        if (permissions.isEmpty()) {
            sync(player, claim, "No valid target and action combinations selected.", true);
            return;
        }
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(player.getServer());
        if (payload.enabled()) {
            long additions = permissions.stream()
                    .filter(permission -> !data.contains(permission))
                    .count();
            if (additions > data.remainingCapacity(claim.ownerId())) {
                sync(player, claim,
                        "The permission limit for this claim owner would be exceeded.",
                        true);
                return;
            }
        }

        int changed = 0;
        for (ClaimPermissionKey permission : permissions) {
            if (payload.enabled() ? data.add(permission) : data.remove(permission)) {
                changed++;
            }
        }
        String operation = payload.enabled() ? "enabled" : "removed";
        String status = changed == 0
                ? "Nothing changed. The selected permissions already had that state."
                : changed + " permissions " + operation + " in one update.";
        sync(player, claim, status, false);
    }

    private static void applyChanges(ServerPlayer player, OpenPacCompat.Claim claim,
                                     ClaimPermissionsBatchPayload payload) {
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(player.getServer());
        Set<ClaimPermissionKey> existing = new LinkedHashSet<>(
                data.list(claim.ownerId(), claim.subConfigIndex())
        );
        Map<ClaimPermissionKey, Boolean> mutations = new LinkedHashMap<>();
        for (ClaimPermissionsBatchPayload.TargetEntry targetEntry : payload.targets()) {
            ParsedTarget parsed = parseTarget(targetEntry);
            if (parsed == null) {
                sync(player, claim, "A selected permission target is no longer valid.", true);
                return;
            }
            ClaimPermissionAction action;
            try {
                action = ClaimPermissionAction.valueOf(targetEntry.action());
            } catch (IllegalArgumentException exception) {
                sync(player, claim, "A selected permission action is no longer valid.", true);
                return;
            }
            if (!parsed.target().supports(action)) {
                sync(player, claim, "That action is not valid for this category.", true);
                return;
            }
            UUID selectedPlayer = resolveEntryPlayer(player, targetEntry);
            if (INVALID_PLAYER.equals(selectedPlayer)) {
                sync(player, claim,
                        "Player not found: " + targetEntry.playerName() + ".", true);
                return;
            }
            ClaimPermissionKey permission = key(
                    claim,
                    parsed.target(),
                    parsed.targetId(),
                    action,
                    selectedPlayer
            );
            if (targetEntry.enabled()
                    && selectedPlayer != null
                    && !existing.contains(permission)
                    && !isKnownPlayer(player, selectedPlayer)) {
                sync(player, claim,
                        "Player not found: " + targetEntry.playerName() + ".", true);
                return;
            }
            mutations.put(permission, targetEntry.enabled());
        }

        long removals = mutations.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .filter(data::contains)
                .count();
        long additions = mutations.entrySet().stream()
                .filter(entry -> entry.getValue())
                .map(Map.Entry::getKey)
                .filter(permission -> !data.contains(permission))
                .count();
        if (additions > data.remainingCapacity(claim.ownerId()) + removals) {
            sync(player, claim,
                    "The permission limit for this claim owner would be exceeded.",
                    true);
            return;
        }

        int changed = 0;
        for (Map.Entry<ClaimPermissionKey, Boolean> mutation : mutations.entrySet()) {
            if (!mutation.getValue() && data.remove(mutation.getKey())) {
                changed++;
            }
        }
        for (Map.Entry<ClaimPermissionKey, Boolean> mutation : mutations.entrySet()) {
            if (mutation.getValue() && data.add(mutation.getKey())) {
                changed++;
            }
        }
        sync(player, claim,
                changed == 1 ? "1 permission change saved."
                        : changed + " permission changes saved.",
                false);
    }

    private static Set<ClaimPermissionAction> parseActionSet(List<String> values) {
        Set<ClaimPermissionAction> result = new LinkedHashSet<>();
        for (String value : values) {
            try {
                result.add(ClaimPermissionAction.valueOf(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private static ParsedTarget parseTarget(
            ClaimPermissionsBatchPayload.TargetEntry entry) {
        try {
            ClaimPermissionTarget target = ClaimPermissionTarget.valueOf(entry.target());
            ResourceLocation targetId = ResourceLocation.tryParse(entry.targetId());
            if (targetId == null || !target.isRegistered(targetId)) {
                return null;
            }
            return new ParsedTarget(target, targetId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PlayerSelection resolveSelectedPlayers(ServerPlayer player,
                                                          List<String> names) {
        List<UUID> result = new ArrayList<>();
        for (String name : names) {
            UUID selectedPlayer = resolvePlayerName(player, name);
            if (INVALID_PLAYER.equals(selectedPlayer)) {
                return new PlayerSelection(
                        List.of(),
                        "Player not found: " + name + "."
                );
            }
            if (selectedPlayer == null) {
                result.clear();
                result.add(null);
                return new PlayerSelection(result, null);
            }
            if (!result.contains(selectedPlayer)) {
                result.add(selectedPlayer);
            }
        }
        return new PlayerSelection(result, null);
    }

    private static UUID resolveEntryPlayer(
            ServerPlayer player,
            ClaimPermissionsBatchPayload.TargetEntry entry) {
        if (!entry.playerId().isBlank()) {
            try {
                return UUID.fromString(entry.playerId());
            } catch (IllegalArgumentException exception) {
                return INVALID_PLAYER;
            }
        }
        return resolvePlayerName(player, entry.playerName());
    }

    private static List<ClaimPermissionAction> parseActions(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(action -> !action.isEmpty())
                .map(ClaimPermissionAction::valueOf)
                .distinct()
                .toList();
    }

    private static UUID resolvePlayer(ServerPlayer player,
                                      ClaimPermissionMutationPayload payload) {
        if (payload.operation() != ClaimPermissionMutationPayload.Operation.ADD
                && !payload.playerId().isBlank()) {
            try {
                return UUID.fromString(payload.playerId());
            } catch (IllegalArgumentException exception) {
                return INVALID_PLAYER;
            }
        }
        return resolvePlayerName(player, payload.playerName());
    }

    private static UUID resolvePlayerName(ServerPlayer player, String rawName) {
        String playerName = rawName.trim();
        if (playerName.isEmpty() || playerName.equalsIgnoreCase("all")) {
            return null;
        }
        ServerPlayer onlinePlayer = player.getServer()
                .getPlayerList()
                .getPlayerByName(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUUID();
        }
        Optional<GameProfile> cachedProfile = player.getServer()
                .getProfileCache()
                .get(playerName);
        if (cachedProfile.isEmpty()) {
            return INVALID_PLAYER;
        }
        UUID playerId = cachedProfile.get().getId();
        return player.getServer().usesAuthentication() || hasJoined(player, playerId)
                ? playerId
                : INVALID_PLAYER;
    }

    private static boolean isKnownPlayer(ServerPlayer player, UUID playerId) {
        if (player.getServer().getPlayerList().getPlayer(playerId) != null) {
            return true;
        }
        if (!player.getServer().usesAuthentication()) {
            return hasJoined(player, playerId);
        }
        return player.getServer().getProfileCache().get(playerId).isPresent()
                || hasJoined(player, playerId);
    }

    private static boolean hasJoined(ServerPlayer player, UUID playerId) {
        return Files.isRegularFile(
                player.getServer()
                        .getWorldPath(LevelResource.PLAYER_DATA_DIR)
                        .resolve(playerId + ".dat")
        );
    }

    private static ClaimPermissionKey key(OpenPacCompat.Claim claim,
                                          ClaimPermissionTarget target,
                                          ResourceLocation targetId,
                                          ClaimPermissionAction action,
                                          UUID selectedPlayer) {
        return new ClaimPermissionKey(
                claim.ownerId(),
                claim.subConfigIndex(),
                target,
                targetId,
                action,
                selectedPlayer
        );
    }

    private static void sync(ServerPlayer player, OpenPacCompat.Claim claim,
                             String status, boolean error) {
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(player.getServer());
        List<ClaimPermissionKey> permissions = data.list(
                claim.ownerId(), claim.subConfigIndex()
        );
        if (permissions.size() > ClaimPermissionsSavedData.MAX_PERMISSIONS_PER_OWNER) {
            status = "This claim owner has too many saved permissions."
                    + " Only the first "
                    + ClaimPermissionsSavedData.MAX_PERMISSIONS_PER_OWNER
                    + " can be displayed.";
            error = true;
        }
        List<ClaimPermissionsSyncPayload.Entry> entries = permissions
                .stream()
                .limit(ClaimPermissionsSavedData.MAX_PERMISSIONS_PER_OWNER)
                .map(permission -> new ClaimPermissionsSyncPayload.Entry(
                        permission.target().name(),
                        permission.targetId().toString(),
                        permission.action().name(),
                        permission.player() == null
                                ? ""
                                : permission.player().toString(),
                        playerName(player, permission.player())
                ))
                .toList();
        ClaimPermissionsNetwork.sendTo(
                player,
                new ClaimPermissionsSyncPayload(
                        claim.ownerId(),
                        claim.subConfigIndex(),
                        playerName(player, claim.ownerId()),
                        scopeName(claim),
                        !claim.ownerId().equals(player.getUUID()),
                        status,
                        error,
                        entries
                )
        );
    }

    private static void rejectMutation(ServerPlayer player, UUID owner,
                                       int subConfigIndex, String status) {
        player.sendSystemMessage(Component.literal(status).withStyle(ChatFormatting.RED));
        ClaimPermissionsNetwork.sendTo(
                player,
                new ClaimPermissionsSyncPayload(
                        owner,
                        subConfigIndex,
                        playerName(player, owner),
                        scopeName(new OpenPacCompat.Claim(owner, subConfigIndex)),
                        !owner.equals(player.getUUID()),
                        status,
                        true,
                        List.of()
                )
        );
    }

    private static String playerName(ServerPlayer player, UUID playerId) {
        if (playerId == null) {
            return "All players";
        }
        return player.getServer().getProfileCache()
                .get(playerId)
                .map(GameProfile::getName)
                .orElse(playerId.toString());
    }

    private static OpenPacCompat.Claim currentPayloadClaim(ServerPlayer player,
                                                            UUID owner,
                                                            int subConfigIndex) {
        OpenPacCompat.Claim claim = getEditableClaim(player, false);
        if (claim == null) {
            rejectMutation(player, owner, subConfigIndex,
                    "Stand inside a claim you can manage before changing permissions.");
            return null;
        }
        if (!claim.ownerId().equals(owner)
                || claim.subConfigIndex() != subConfigIndex) {
            rejectMutation(player, owner, subConfigIndex,
                    "The selected claim no longer matches your position.");
            return null;
        }
        return claim;
    }

    private static OpenPacCompat.Claim getEditableClaim(ServerPlayer player,
                                                        boolean sendError) {
        OpenPacCompat.Claim claim = OpenPacCompat.getClaimAt(
                player.getServer(),
                player.level().dimension().location(),
                player.chunkPosition()
        );
        if (claim == null) {
            if (sendError) {
                player.sendSystemMessage(Component.literal(
                        "Stand inside a claim or subclaim first."
                ).withStyle(ChatFormatting.RED));
            }
            return null;
        }
        if (!claim.ownerId().equals(player.getUUID()) && !isAdmin(player)) {
            if (sendError) {
                player.sendSystemMessage(Component.literal(
                        "Only the owner or an admin can change permissions for this claim."
                ).withStyle(ChatFormatting.RED));
            }
            return null;
        }
        return claim;
    }

    private static boolean isAdmin(ServerPlayer player) {
        return player.createCommandSourceStack().hasPermission(2);
    }

    private static boolean allowMutation(ServerPlayer player) {
        long now = System.nanoTime();
        long next = NEXT_MUTATION.getOrDefault(player.getUUID(), 0L);
        if (now < next) {
            return false;
        }
        NEXT_MUTATION.put(player.getUUID(), now + MUTATION_INTERVAL_NANOS);
        if (NEXT_MUTATION.size() > 1024) {
            NEXT_MUTATION.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        return true;
    }

    private static String scopeName(OpenPacCompat.Claim claim) {
        return claim.subConfigIndex() < 0
                ? "Main claim"
                : "Subclaim #" + claim.subConfigIndex();
    }

    private record ParsedTarget(ClaimPermissionTarget target,
                                ResourceLocation targetId) {
    }

    private record PlayerSelection(List<UUID> players, String error) {
    }

}
