package brassworks.opac_essentials.permission;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import brassworks.opac_essentials.compat.OpenPacCompat;
import brassworks.opac_essentials.network.ClaimPermissionMutationPayload;
import brassworks.opac_essentials.network.ClaimPermissionsNetwork;
import brassworks.opac_essentials.network.ClaimPermissionsSyncPayload;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ClaimPermissionsUiService {
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

        OpenPacCompat.Claim claim = getOwnedClaim(player, true);
        if (claim == null) {
            return 0;
        }
        sync(player, claim, "", false);
        return 1;
    }

    public static void mutate(ServerPlayer player,
                              ClaimPermissionMutationPayload payload) {
        OpenPacCompat.Claim claim = getOwnedClaim(player, true);
        if (claim == null) {
            return;
        }
        if (!claim.ownerId().equals(payload.claimOwner())
                || claim.subConfigIndex() != payload.subConfigIndex()) {
            sync(player, claim,
                    "You are no longer inside the claim this screen was opened for.",
                    true);
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
                            ? "That permission already exists."
                            : "Those permissions already exist.";
                    error = true;
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

    private static List<ClaimPermissionAction> parseActions(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(action -> !action.isEmpty())
                .map(ClaimPermissionAction::valueOf)
                .distinct()
                .toList();
    }

    private static final UUID INVALID_PLAYER = new UUID(0L, 0L);

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

        String playerName = payload.playerName().trim();
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
        return cachedProfile.map(GameProfile::getId).orElse(INVALID_PLAYER);
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
        List<ClaimPermissionsSyncPayload.Entry> entries =
                ClaimPermissionsSavedData.get(player.getServer())
                        .list(claim.ownerId(), claim.subConfigIndex())
                        .stream()
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
                        scopeName(claim),
                        status,
                        error,
                        entries
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

    private static OpenPacCompat.Claim getOwnedClaim(ServerPlayer player,
                                                     boolean sendError) {
        OpenPacCompat.Claim claim = OpenPacCompat.getClaimAt(
                player.getServer(),
                player.level().dimension().location(),
                player.chunkPosition()
        );
        if (claim == null) {
            if (sendError) {
                player.sendSystemMessage(Component.literal(
                        "Stand inside one of your claims or subclaims first."
                ).withStyle(ChatFormatting.RED));
            }
            return null;
        }
        if (!claim.ownerId().equals(player.getUUID())) {
            if (sendError) {
                player.sendSystemMessage(Component.literal(
                        "Only the owner can change permissions for this claim."
                ).withStyle(ChatFormatting.RED));
            }
            return null;
        }
        return claim;
    }

    private static String scopeName(OpenPacCompat.Claim claim) {
        return claim.subConfigIndex() < 0
                ? "Main claim"
                : "Subclaim #" + claim.subConfigIndex();
    }
}
