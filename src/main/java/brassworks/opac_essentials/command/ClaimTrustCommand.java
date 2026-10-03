package brassworks.opac_essentials.command;

import brassworks.opac_essentials.claims.permission.server.ClaimPermissionsSavedData;
import brassworks.opac_essentials.compat.openpac.OpenPacCompat;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClaimTrustCommand {
    public LiteralArgumentBuilder<CommandSourceStack> create(boolean trust) {
        return Commands.literal(trust ? "trust" : "untrust")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .suggests(trust ? this::suggestOnlinePlayers : this::suggestTrustedPlayers)
                        .executes(context -> change(context, trust)));
    }

    private int change(CommandContext<CommandSourceStack> context,
                       boolean trust) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        OpenPacCompat.Claim claim = getEditableClaim(player);
        if (claim == null) {
            return 0;
        }

        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(
                context, "player"
        );
        if (profiles.size() != 1) {
            source.sendFailure(Component.literal("Select exactly one player."));
            return 0;
        }

        GameProfile target = profiles.iterator().next();
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(Objects.requireNonNull(player.getServer()));
        boolean changed = trust
                ? data.trust(claim.ownerId(), claim.subConfigIndex(), target.getId())
                : data.untrust(claim.ownerId(), claim.subConfigIndex(), target.getId());
        if (!changed) {
            source.sendFailure(Component.literal(
                    target.getName() + (trust
                            ? " is already trusted in this claim."
                            : " is not trusted in this claim.")
            ));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(target.getName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(trust
                                ? " now has full permissions in this claim."
                                : " no longer has full permissions in this claim.")),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestOnlinePlayers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                context.getSource().getOnlinePlayerNames(), builder
        );
    }

    private CompletableFuture<Suggestions> suggestTrustedPlayers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            return builder.buildFuture();
        }
        OpenPacCompat.Claim claim = getEditableClaim(player, false);
        if (claim == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                ClaimPermissionsSavedData.get(player.getServer())
                        .listTrusted(claim.ownerId(), claim.subConfigIndex())
                        .stream()
                        .map(playerId -> player.getServer().getProfileCache()
                                .get(playerId)
                                .map(GameProfile::getName)
                                .orElse(playerId.toString())),
                builder
        );
    }

    private OpenPacCompat.Claim getEditableClaim(ServerPlayer player) {
        return getEditableClaim(player, true);
    }

    private OpenPacCompat.Claim getEditableClaim(ServerPlayer player,
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
        if (!claim.ownerId().equals(player.getUUID())
                && !player.createCommandSourceStack().hasPermission(2)) {
            if (sendError) {
                player.sendSystemMessage(Component.literal(
                        "Only the owner or an admin can change trust for this claim."
                ).withStyle(ChatFormatting.RED));
            }
            return null;
        }
        return claim;
    }
}
