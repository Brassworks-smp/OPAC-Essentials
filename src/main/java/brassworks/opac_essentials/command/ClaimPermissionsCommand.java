package brassworks.opac_essentials.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import brassworks.opac_essentials.compat.OpenPacCompat;
import brassworks.opac_essentials.network.ClaimPermissionsNetwork;
import brassworks.opac_essentials.permission.ClaimPermissionAction;
import brassworks.opac_essentials.permission.ClaimPermissionKey;
import brassworks.opac_essentials.permission.ClaimPermissionTarget;
import brassworks.opac_essentials.permission.ClaimPermissionsSavedData;
import brassworks.opac_essentials.permission.ClaimPermissionsUiService;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClaimPermissionsCommand {
    private static final String TARGET_ID_ARGUMENT = "target-id";

    public LiteralArgumentBuilder<CommandSourceStack> create() {
        LiteralArgumentBuilder<CommandSourceStack> permissions = Commands.literal("permissions")
                .executes(this::open)
                .then(Commands.literal("list").executes(this::list));

        LiteralArgumentBuilder<CommandSourceStack> addOperation = Commands.literal("add");
        for (ClaimPermissionTarget target : ClaimPermissionTarget.values()) {
            LiteralArgumentBuilder<CommandSourceStack> targetNode =
                    Commands.literal(target.commandName());
            RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> targetIdNode =
                    Commands.argument(TARGET_ID_ARGUMENT, ResourceLocationArgument.id())
                            .suggests((context, builder) ->
                                    SharedSuggestionProvider.suggestResource(
                                            target.registeredIds(), builder
                                    ));

            for (ClaimPermissionAction action : ClaimPermissionAction.values()) {
                if (!target.supports(action)) {
                    continue;
                }

                LiteralArgumentBuilder<CommandSourceStack> actionNode =
                        Commands.literal(action.commandName())
                                .executes(context -> change(
                                        context.getSource(),
                                        true,
                                        target,
                                        ResourceLocationArgument.getId(
                                                context, TARGET_ID_ARGUMENT
                                        ),
                                        action,
                                        null,
                                        "all players"
                                ))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        context.getSource().getOnlinePlayerNames(),
                                                        builder
                                                ))
                                        .executes(context -> addForPlayer(
                                                context,
                                                target,
                                                ResourceLocationArgument.getId(
                                                        context, TARGET_ID_ARGUMENT
                                                ),
                                                action
                                        )));
                targetIdNode.then(actionNode);
            }
            targetNode.then(targetIdNode);
            addOperation.then(targetNode);
        }
        permissions.then(addOperation);
        permissions.then(Commands.literal("remove")
                .then(Commands.argument("permission", ResourceLocationArgument.id())
                        .suggests(this::suggestGrantedPermissions)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestGrantedPlayers)
                                .executes(this::remove))));

        return permissions;
    }

    private int open(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (ClaimPermissionsNetwork.canUseUi(player)) {
                return ClaimPermissionsUiService.open(context.getSource());
            }
        } catch (CommandSyntaxException ignored) {
            return ClaimPermissionsUiService.open(context.getSource());
        }
        return list(context);
    }

    private int addForPlayer(CommandContext<CommandSourceStack> context,
                             ClaimPermissionTarget target, ResourceLocation targetId,
                             ClaimPermissionAction action) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            context.getSource().sendFailure(Component.literal("Select exactly one player."));
            return 0;
        }
        GameProfile targetPlayer = profiles.iterator().next();
        return change(
                context.getSource(), true, target, targetId, action,
                targetPlayer.getId(), targetPlayer.getName()
        );
    }

    private CompletableFuture<Suggestions> suggestGrantedPermissions(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        OpenPacCompat.Claim claim = getOwnedClaimSilently(context.getSource());
        if (claim == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggestResource(
                getPermissions(context.getSource(), claim).stream()
                        .map(this::permissionName)
                        .distinct(),
                builder
        );
    }

    private CompletableFuture<Suggestions> suggestGrantedPlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        OpenPacCompat.Claim claim = getOwnedClaimSilently(context.getSource());
        PermissionSelection selection = parsePermissionName(
                ResourceLocationArgument.getId(context, "permission")
        );
        if (claim == null || selection == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                getPermissions(context.getSource(), claim).stream()
                        .filter(permission -> matches(permission, selection))
                        .map(permission -> playerName(context.getSource(), permission))
                        .distinct(),
                builder
        );
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        PermissionSelection selection = parsePermissionName(
                ResourceLocationArgument.getId(context, "permission")
        );
        if (selection == null) {
            context.getSource().sendFailure(Component.literal("Unknown permission."));
            return 0;
        }

        OpenPacCompat.Claim claim = getOwnedClaim(context.getSource());
        if (claim == null) {
            return 0;
        }

        String selectedPlayer = StringArgumentType.getString(context, "player");
        ClaimPermissionKey permission = getPermissions(context.getSource(), claim).stream()
                .filter(entry -> matches(entry, selection))
                .filter(entry -> playerName(context.getSource(), entry)
                        .equalsIgnoreCase(selectedPlayer))
                .findFirst()
                .orElse(null);
        if (permission == null) {
            context.getSource().sendFailure(Component.literal(
                    "That permission is not granted to " + selectedPlayer + "."
            ));
            return 0;
        }

        return change(
                context.getSource(), false, permission.target(), permission.targetId(),
                permission.action(), permission.player(),
                permission.player() == null ? "all players" : selectedPlayer
        );
    }

    private List<ClaimPermissionKey> getPermissions(CommandSourceStack source,
                                                    OpenPacCompat.Claim claim) {
        return ClaimPermissionsSavedData.get(source.getServer())
                .list(claim.ownerId(), claim.subConfigIndex());
    }

    private ResourceLocation permissionName(ClaimPermissionKey permission) {
        return ResourceLocation.fromNamespaceAndPath(
                permission.target().commandName(),
                permission.targetId().getNamespace() + "/" + permission.targetId().getPath()
                        + "/" + permission.action().commandName()
        );
    }

    private PermissionSelection parsePermissionName(ResourceLocation value) {
        String path = value.getPath();
        int firstSeparator = path.indexOf('/');
        int lastSeparator = path.lastIndexOf('/');
        if (firstSeparator <= 0 || lastSeparator <= firstSeparator) {
            return null;
        }

        ClaimPermissionTarget target = null;
        for (ClaimPermissionTarget candidate : ClaimPermissionTarget.values()) {
            if (candidate.commandName().equals(value.getNamespace())) {
                target = candidate;
                break;
            }
        }
        ResourceLocation targetId = ResourceLocation.tryParse(
                path.substring(0, firstSeparator) + ":"
                        + path.substring(firstSeparator + 1, lastSeparator)
        );
        ClaimPermissionAction action = null;
        for (ClaimPermissionAction candidate : ClaimPermissionAction.values()) {
            if (candidate.commandName().equals(path.substring(lastSeparator + 1))) {
                action = candidate;
                break;
            }
        }
        if (target == null || targetId == null || action == null) {
            return null;
        }
        return new PermissionSelection(target, targetId, action);
    }

    private boolean matches(ClaimPermissionKey permission, PermissionSelection selection) {
        return permission.target() == selection.target()
                && permission.targetId().equals(selection.targetId())
                && permission.action() == selection.action();
    }

    private String playerName(CommandSourceStack source, ClaimPermissionKey permission) {
        if (permission.player() == null) {
            return "all";
        }
        return source.getServer().getProfileCache()
                .get(permission.player())
                .map(GameProfile::getName)
                .orElse(permission.player().toString());
    }

    private OpenPacCompat.Claim getOwnedClaimSilently(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        OpenPacCompat.Claim claim = OpenPacCompat.getClaimAt(
                player.getServer(),
                player.level().dimension().location(),
                player.chunkPosition()
        );
        return claim != null && claim.ownerId().equals(player.getUUID()) ? claim : null;
    }

    private OpenPacCompat.Claim getOwnedClaim(CommandSourceStack source) {
        try {
            return getOwnedClaim(source.getPlayerOrException());
        } catch (CommandSyntaxException exception) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return null;
        }
    }

    private int change(CommandSourceStack source, boolean add, ClaimPermissionTarget target,
                       ResourceLocation targetId, ClaimPermissionAction action,
                       UUID playerId, String playerName) {
        if (!target.supports(action)) {
            source.sendFailure(Component.literal(
                    action.commandName() + " is not valid for " + target.commandName() + "."
            ));
            return 0;
        }
        if (!target.isRegistered(targetId)) {
            source.sendFailure(Component.literal(
                    "Unknown " + target.commandName() + " type: " + targetId
            ));
            return 0;
        }

        ServerPlayer claimOwner;
        try {
            claimOwner = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "This command can only be used by a player."
            ));
            return 0;
        }

        OpenPacCompat.Claim claim = getOwnedClaim(claimOwner);
        if (claim == null) {
            return 0;
        }

        ClaimPermissionKey key = new ClaimPermissionKey(
                claim.ownerId(),
                claim.subConfigIndex(),
                target,
                targetId,
                action,
                playerId
        );
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(claimOwner.getServer());
        boolean changed = add ? data.add(key) : data.remove(key);

        if (!changed) {
            source.sendFailure(Component.literal(
                    add
                            ? "That permission already exists."
                            : "That permission does not exist."
            ));
            return 0;
        }

        String operation = add ? "Granted" : "Removed";
        String preposition = add ? "to " : "from ";
        source.sendSuccess(
                () -> Component.literal(operation + " ")
                        .append(Component.literal(
                                target.commandName() + " " + targetId + " "
                                        + action.commandName()
                        ).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(
                                " " + preposition + playerName + " for "
                                        + scopeName(claim) + "."
                        )),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer claimOwner;
        try {
            claimOwner = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal(
                    "This command can only be used by a player."
            ));
            return 0;
        }

        OpenPacCompat.Claim claim = getOwnedClaim(claimOwner);
        if (claim == null) {
            return 0;
        }

        List<ClaimPermissionKey> entries =
                ClaimPermissionsSavedData.get(claimOwner.getServer())
                        .list(claim.ownerId(), claim.subConfigIndex());
        if (entries.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "No custom permissions are set for " + scopeName(claim) + "."
                    ),
                    false
            );
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(
                () -> Component.literal("Permissions for " + scopeName(claim) + ":")
                        .withStyle(ChatFormatting.GOLD),
                false
        );
        for (ClaimPermissionKey entry : entries) {
            String playerName = entry.player() == null
                    ? "all players"
                    : claimOwner.getServer().getProfileCache()
                    .get(entry.player())
                    .map(profile -> profile.getName() + " (" + entry.player() + ")")
                    .orElse(entry.player().toString());
            source.sendSuccess(
                    () -> Component.literal("- ")
                            .append(Component.literal(
                                    entry.target().commandName() + " "
                                            + entry.targetId() + " "
                                            + entry.action().commandName()
                            ).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" -> " + playerName)),
                    false
            );
        }
        return entries.size();
    }

    private OpenPacCompat.Claim getOwnedClaim(ServerPlayer player) {
        OpenPacCompat.Claim claim = OpenPacCompat.getClaimAt(
                player.getServer(),
                player.level().dimension().location(),
                player.chunkPosition()
        );
        if (claim == null) {
            player.sendSystemMessage(
                    Component.literal(
                            "Stand inside one of your claims or subclaims first."
                    ).withStyle(ChatFormatting.RED)
            );
            return null;
        }
        if (!claim.ownerId().equals(player.getUUID())) {
            player.sendSystemMessage(
                    Component.literal(
                            "Only the owner can change permissions for this claim."
                    ).withStyle(ChatFormatting.RED)
            );
            return null;
        }
        return claim;
    }

    private String scopeName(OpenPacCompat.Claim claim) {
        return claim.subConfigIndex() < 0
                ? "the main claim"
                : "subclaim #" + claim.subConfigIndex();
    }

    private record PermissionSelection(ClaimPermissionTarget target,
                                       ResourceLocation targetId,
                                       ClaimPermissionAction action) {
    }
}
