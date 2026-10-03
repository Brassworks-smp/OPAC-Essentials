package brassworks.opac_essentials.claims.purchase;

import brassworks.opac_essentials.claims.menu.ClaimQuickConfigPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

public final class ClaimPurchaseService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClaimPurchaseService() {
    }

    public static void sendSettings(ServerPlayer player) {
        OpenPACServerAPI api = OpenPACServerAPI.get(player.getServer());
        IPlayerConfigManagerAPI configs = api.getPlayerConfigManager();
        IPlayerConfigAPI playerConfig = configs.getLoadedConfig(player.getUUID());
        if (playerConfig == null) {
            send(player, "Your OPAC profile is not loaded yet.", ChatFormatting.RED);
            return;
        }
        IPlayerConfigAPI claimConfig = playerConfig.getUsedSubConfig();
        if (claimConfig == null) {
            claimConfig = playerConfig;
        }
        IServerClaimsManagerAPI claims = api.getServerClaimsManager();
        IServerPlayerClaimInfoAPI claimInfo = claims.getPlayerInfo(player.getUUID());
        IServerPartyAPI party = api.getPartyManager().getPartyByMember(player.getUUID());
        Item currency = BuiltInRegistries.ITEM.get(ClaimPurchaseRules.currencyItem());
        String currencyName = currency == Items.AIR
                ? ClaimPurchaseRules.currencyItem().toString()
                : currency.getDescription().getString();
        String partyName = "No party";
        String partyOwner = "N/A";
        int partyMembers = 0;
        int partyAllies = 0;
        int partyInvites = 0;
        boolean partyNameEditable = false;
        if (party != null) {
            partyOwner = party.getOwner().getUsername();
            partyNameEditable = party.getOwner().getUUID().equals(player.getUUID());
            IPlayerConfigAPI ownerConfig = configs.getLoadedConfig(
                    party.getOwner().getUUID()
            );
            if (ownerConfig == null) {
                partyName = party.getDefaultName();
            } else {
                String customName = ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME);
                partyName = customName.isBlank() ? party.getDefaultName() : customName;
            }
            partyMembers = party.getMemberCount();
            partyAllies = party.getAllyCount();
            partyInvites = party.getInviteCount();
        }
        PacketDistributor.sendToPlayer(
                player,
                new ClaimPurchaseSettingsPayload(
                        ClaimPurchaseRules.currencyItem().toString(),
                        currencyName,
                        ClaimPurchaseRules.pricePerClaim(),
                        claims.getPlayerFullClaimLimit(player),
                        claimInfo == null ? 0 : claimInfo.getClaimCount(),
                        claimInfo == null ? 0 : claimInfo.getForceloadCount(),
                        claims.getPlayerFullForceloadLimit(player),
                        partyName,
                        partyOwner,
                        partyNameEditable,
                        partyMembers,
                        partyAllies,
                        partyInvites,
                        claimConfig.getEffective(PlayerConfigOptions.CLAIMS_COLOR)
                )
        );
    }

    public static void updateQuickConfig(ServerPlayer player,
                                         ClaimQuickConfigPayload payload) {
        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(player.getServer());
            IPlayerConfigAPI playerConfig = api.getPlayerConfigManager()
                    .getLoadedConfig(player.getUUID());
            if (playerConfig == null) {
                send(player, "Your OPAC profile is not loaded yet.", ChatFormatting.RED);
                return;
            }
            IPlayerConfigAPI.SetResult result;
            if (payload.operation() == ClaimQuickConfigPayload.Operation.PARTY_NAME) {
                IServerPartyAPI party = api.getPartyManager()
                        .getPartyByOwner(player.getUUID());
                if (party == null) {
                    send(player, "Only the party owner can change the party name.",
                            ChatFormatting.RED);
                    sendSettings(player);
                    return;
                }
                String partyName = payload.partyName().trim();
                if (!validPartyName(partyName)) {
                    send(player, "Invalid party name.", ChatFormatting.RED);
                    sendSettings(player);
                    return;
                }
                result = playerConfig.tryToSet(
                        PlayerConfigOptions.PARTY_NAME,
                        partyName
                );
            } else {
                if ((payload.claimColor() & ~0xFFFFFF) != 0) {
                    send(player, "Invalid claim color.", ChatFormatting.RED);
                    sendSettings(player);
                    return;
                }
                IPlayerConfigAPI claimConfig = playerConfig.getUsedSubConfig();
                if (claimConfig == null) {
                    claimConfig = playerConfig;
                }
                result = claimConfig.tryToSet(
                        PlayerConfigOptions.CLAIMS_COLOR,
                        payload.claimColor()
                );
            }
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) {
                send(player, "This quick config could not be changed.",
                        ChatFormatting.RED);
                sendSettings(player);
                return;
            }
            sendSettings(player);
            send(player, "Quick config saved.", ChatFormatting.LIGHT_PURPLE);
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to update OPAC quick config for {}",
                    player.getGameProfile().getName(),
                    exception
            );
            send(player, "The quick config could not be changed.",
                    ChatFormatting.RED);
        }
    }

    public static void purchase(ServerPlayer player, int amount) {
        if (amount < 1 || amount > ClaimPurchaseRules.MAX_PURCHASE) {
            send(player, "Invalid claim amount.", ChatFormatting.RED);
            return;
        }

        try {
            int totalCost = Math.multiplyExact(amount, ClaimPurchaseRules.pricePerClaim());
            Item currency = BuiltInRegistries.ITEM.get(ClaimPurchaseRules.currencyItem());
            if (currency == Items.AIR) {
                send(player, "Claim purchases are currently unavailable.", ChatFormatting.RED);
                return;
            }

            int available = count(player, currency);
            if (available < totalCost) {
                send(
                        player,
                        "You need " + totalCost + " x "
                                + currency.getDescription().getString()
                                + ", but only have " + available + ".",
                        ChatFormatting.RED
                );
                return;
            }

            IPlayerConfigManagerAPI configs = OpenPACServerAPI.get(player.getServer())
                    .getPlayerConfigManager();
            IPlayerConfigAPI config = configs.getLoadedConfig(player.getUUID());
            if (config == null) {
                send(player, "Your OPAC profile is not loaded yet.", ChatFormatting.RED);
                return;
            }

            int current = config.getEffective(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
            if (current > Integer.MAX_VALUE - amount) {
                send(player, "The extra claims could not be applied.", ChatFormatting.RED);
                return;
            }
            IPlayerConfigAPI.SetResult result = config.tryToSet(
                    PlayerConfigOptions.BONUS_CHUNK_CLAIMS,
                    current + amount
            );
            if (result != IPlayerConfigAPI.SetResult.SUCCESS) {
                send(player, "The extra claims could not be applied.", ChatFormatting.RED);
                return;
            }

            remove(player, currency, totalCost);
            player.getInventory().setChanged();
            sendSettings(player);
            send(
                    player,
                    "Purchased " + amount + " extra "
                            + (amount == 1 ? "claim" : "claims")
                            + " for " + totalCost + " x "
                            + currency.getDescription().getString() + ".",
                    ChatFormatting.LIGHT_PURPLE
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to purchase OPAC claims for {}",
                    player.getGameProfile().getName(),
                    exception
            );
            send(player, "The claim purchase failed.", ChatFormatting.RED);
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void remove(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining == 0) {
                return;
            }
            if (stack.is(item)) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
    }

    private static boolean validPartyName(String value) {
        if (value.isEmpty() || value.length() > 100) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetter(character)
                    && !Character.isDigit(character)
                    && " _'\"!?,-&%*():".indexOf(character) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void send(ServerPlayer player, String message,
                             ChatFormatting color) {
        player.sendSystemMessage(Component.literal(message).withStyle(color));
    }
}
