package brassworks.opac_essentials.party.menu.server;

import brassworks.opac_essentials.party.menu.network.PartyMenuActionPayload;
import brassworks.opac_essentials.party.menu.network.PartyMenuStatePayload;
import brassworks.opac_essentials.partychat.PartyChatSavedData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import xaero.pac.common.parties.party.api.IPartyPlayerInfoAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PartyMenuService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_MENU_ENTRIES = 512;
    private static final long OPEN_INTERVAL_NANOS = 1_000_000_000L;
    private static final long REFRESH_INTERVAL_NANOS = 2_000_000_000L;
    private static final long ACTION_INTERVAL_NANOS = 100_000_000L;
    private static final Map<UUID, Long> NEXT_SNAPSHOT_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ACTION = new HashMap<>();

    private PartyMenuService() {
    }

    public static void handle(ServerPlayer player,
                              PartyMenuActionPayload payload) {
        try {
            if (payload.action() == PartyMenuActionPayload.Action.OPEN) {
                if (allowRequest(
                        NEXT_SNAPSHOT_REQUEST,
                        player.getUUID(),
                        OPEN_INTERVAL_NANOS
                )) {
                    sendOpenSnapshot(player);
                }
                return;
            }
            if (payload.action() == PartyMenuActionPayload.Action.REFRESH) {
                if (allowRequest(
                        NEXT_SNAPSHOT_REQUEST,
                        player.getUUID(),
                        REFRESH_INTERVAL_NANOS
                )) {
                    sendSnapshot(player);
                }
                return;
            }
            if (!allowRequest(
                    NEXT_ACTION,
                    player.getUUID(),
                    ACTION_INTERVAL_NANOS
            )) {
                return;
            }

            OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
            IPartyManagerAPI parties = api.getPartyManager();
            IServerPartyAPI party = parties.getPartyByMember(player.getUUID());
            IPartyMemberAPI self = party == null
                    ? null
                    : party.getMemberInfo(player.getUUID());
            boolean owner = party != null
                    && party.getOwner().getUUID().equals(player.getUUID());
            boolean canManageMembers = owner || hasRank(self, PartyMemberRank.MODERATOR);
            boolean canManageRanks = owner || hasRank(self, PartyMemberRank.ADMIN);

            switch (payload.action()) {
                case CREATE -> create(player, party);
                case RENAME -> rename(player, api, party, owner, payload.value());
                case TOGGLE_CHAT -> toggleChat(player, party);
                case INVITE -> invite(player, parties, party, canManageMembers, payload.target());
                case CANCEL_INVITE -> cancelInvite(
                        player, party, canManageMembers, payload.target()
                );
                case KICK -> kick(player, party, canManageMembers, payload.target());
                case PROMOTE -> changeRank(
                        player, party, canManageRanks, payload.target(), 1
                );
                case DEMOTE -> changeRank(
                        player, party, canManageRanks, payload.target(), -1
                );
                case TRANSFER -> transfer(player, party, owner, payload.target());
                case LEAVE -> leave(player, party, owner);
                case DISBAND -> disband(player, party, owner);
                default -> {
                }
            }
            sendSnapshot(
                    player,
                    payload.action() == PartyMenuActionPayload.Action.CREATE
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to handle party menu action {} for {}",
                    payload.action(),
                    player.getGameProfile().getName(),
                    exception
            );
            send(player, "The party action could not be completed.", ChatFormatting.RED);
            sendSnapshot(player);
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        sendSnapshot(player, false);
    }

    public static void sendOpenSnapshot(ServerPlayer player) {
        sendSnapshot(player, true);
    }

    private static void sendSnapshot(ServerPlayer player,
                                     boolean openScreen) {
        if (!player.connection.hasChannel(PartyMenuStatePayload.TYPE)) {
            return;
        }
        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(player.server);
            IPartyManagerAPI parties = api.getPartyManager();
            IServerPartyAPI party = parties.getPartyByMember(player.getUUID());
            PartyChatSavedData chatData = PartyChatSavedData.get(player.server);
            if (party == null) {
                chatData.setEnabled(player.getUUID(), false);
                PacketDistributor.sendToPlayer(
                        player,
                        emptySnapshot(openScreen)
                );
                return;
            }

            IPartyMemberAPI self = party.getMemberInfo(player.getUUID());
            boolean owner = party.getOwner().getUUID().equals(player.getUUID());
            boolean canManageMembers = owner || hasRank(self, PartyMemberRank.MODERATOR);
            boolean canManageRanks = owner || hasRank(self, PartyMemberRank.ADMIN);
            List<PartyMenuStatePayload.MemberEntry> members = party
                    .getMemberInfoStream()
                    .limit(MAX_MENU_ENTRIES)
                    .map(member -> memberEntry(player, member))
                    .sorted(Comparator
                            .comparingInt(
                                    (PartyMenuStatePayload.MemberEntry member) ->
                                            member.owner() ? 0 : 1
                            )
                            .thenComparing(Comparator.comparingInt(
                                    (PartyMenuStatePayload.MemberEntry member) ->
                                            rankOrdinal(member.rank())
                            ).reversed())
                            .thenComparing(
                                    PartyMenuStatePayload.MemberEntry::name,
                                    String.CASE_INSENSITIVE_ORDER
                            )
                    )
                    .toList();
            Map<UUID, PartyMenuStatePayload.PlayerEntry> available = new LinkedHashMap<>();
            party.getInvitedPlayersStream()
                    .sorted(Comparator.comparing(
                            IPartyPlayerInfoAPI::getUsername,
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .limit(MAX_MENU_ENTRIES)
                    .forEach(invited -> available.put(
                            invited.getUUID(),
                            new PartyMenuStatePayload.PlayerEntry(
                                    invited.getUUID(),
                                    invited.getUsername(),
                                    true,
                                    player.server.getPlayerList()
                                            .getPlayer(invited.getUUID()) != null
                            )
                    ));
            player.server.getPlayerList().getPlayers().stream()
                    .filter(other -> !other.getUUID().equals(player.getUUID()))
                    .filter(other -> parties.getPartyByMember(other.getUUID()) == null)
                    .sorted(Comparator.comparing(
                            other -> other.getGameProfile().getName(),
                            String.CASE_INSENSITIVE_ORDER
                    ))
                    .limit(MAX_MENU_ENTRIES - available.size())
                    .forEach(other -> available.putIfAbsent(
                            other.getUUID(),
                            new PartyMenuStatePayload.PlayerEntry(
                                    other.getUUID(),
                                    other.getGameProfile().getName(),
                                    party.isInvited(other.getUUID()),
                                    true
                            )
                    ));

            IPlayerConfigAPI ownerConfig = api.getPlayerConfigManager()
                    .getLoadedConfig(party.getOwner().getUUID());
            String partyName = party.getDefaultName();
            if (ownerConfig != null) {
                String customName = ownerConfig.getEffective(PlayerConfigOptions.PARTY_NAME);
                if (!customName.isBlank()) {
                    partyName = customName;
                }
            }
            PacketDistributor.sendToPlayer(
                    player,
                    new PartyMenuStatePayload(
                            true,
                            openScreen,
                            partyName,
                            party.getOwner().getUsername(),
                            owner,
                            owner
                                    ? "OWNER"
                                    : self == null
                                        ? "MEMBER"
                                        : self.getRank().name(),
                            canManageMembers,
                            canManageRanks,
                            chatData.isEnabled(player.getUUID()),
                            party.getAllyCount(),
                            members,
                            new ArrayList<>(available.values())
                    )
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to build party menu state for {}",
                    player.getGameProfile().getName(),
                    exception
            );
            PacketDistributor.sendToPlayer(
                    player,
                    emptySnapshot(openScreen)
            );
        }
    }

    private static void create(ServerPlayer player, IServerPartyAPI party) {
        if (party != null) {
            reject(player, "You are already in a party.");
            return;
        }
        execute(player, "oparties create");
    }

    private static void rename(ServerPlayer player, OpenPACServerAPI api,
                               IServerPartyAPI party, boolean owner,
                               String value) {
        String name = value.trim();
        if (party == null || !owner) {
            reject(player, "Only the party owner can change the party name.");
            return;
        }
        if (!validPartyName(name)) {
            reject(player, "Invalid party name.");
            return;
        }
        IPlayerConfigAPI config = api.getPlayerConfigManager()
                .getLoadedConfig(player.getUUID());
        if (config == null || config.tryToSet(
                PlayerConfigOptions.PARTY_NAME,
                name
        ) != IPlayerConfigAPI.SetResult.SUCCESS) {
            reject(player, "The party name could not be changed.");
            return;
        }
        send(player, "Party name changed.", ChatFormatting.GREEN);
    }

    private static void toggleChat(ServerPlayer player,
                                   IServerPartyAPI party) {
        if (party == null) {
            reject(player, "You are not in a party.");
            return;
        }
        PartyChatSavedData data = PartyChatSavedData.get(player.server);
        boolean enabled = data.toggle(player.getUUID());
        send(
                player,
                "Party chat is now " + (enabled ? "enabled." : "disabled."),
                enabled ? ChatFormatting.GREEN : ChatFormatting.RED
        );
    }

    private static void invite(ServerPlayer player, IPartyManagerAPI parties,
                               IServerPartyAPI party, boolean allowed,
                               UUID targetId) {
        if (!allowed || party == null || targetId == null) {
            reject(player, "You cannot invite players to this party.");
            return;
        }
        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        if (target == null || parties.getPartyByMember(targetId) != null) {
            reject(player, "That player is not available.");
            return;
        }
        execute(player, "oparties member invite " + target.getGameProfile().getName());
    }

    private static void cancelInvite(ServerPlayer player,
                                     IServerPartyAPI party,
                                     boolean allowed, UUID targetId) {
        if (!allowed || party == null || targetId == null
                || !party.isInvited(targetId)) {
            reject(player, "That invitation cannot be removed.");
            return;
        }
        IPartyPlayerInfoAPI target = party.getInvitedPlayersStream()
                .filter(invited -> invited.getUUID().equals(targetId))
                .findFirst()
                .orElse(null);
        if (target == null) {
            reject(player, "That invitation no longer exists.");
            return;
        }
        execute(player, "oparties member kick " + target.getUsername());
    }

    private static void kick(ServerPlayer player, IServerPartyAPI party,
                             boolean allowed, UUID targetId) {
        if (!allowed || party == null || targetId == null) {
            reject(player, "You cannot remove that party member.");
            return;
        }
        IPartyMemberAPI target = party.getMemberInfo(targetId);
        if (target == null || target.isOwner()
                || targetId.equals(player.getUUID())) {
            reject(player, "That party member cannot be removed.");
            return;
        }
        if (execute(player, "oparties member kick " + target.getUsername()) > 0) {
            PartyChatSavedData.get(player.server).setEnabled(targetId, false);
        }
    }

    private static void changeRank(ServerPlayer player, IServerPartyAPI party,
                                   boolean allowed, UUID targetId,
                                   int direction) {
        if (!allowed || party == null || targetId == null) {
            reject(player, "You cannot change that party rank.");
            return;
        }
        IPartyMemberAPI target = party.getMemberInfo(targetId);
        if (target == null || target.isOwner()) {
            reject(player, "That party rank cannot be changed.");
            return;
        }
        PartyMemberRank[] ranks = PartyMemberRank.values();
        int rankIndex = target.getRank().ordinal() + direction;
        if (rankIndex < 0 || rankIndex >= ranks.length) {
            reject(player, "That party member is already at the rank limit.");
            return;
        }
        execute(
                player,
                "oparties member rank " + ranks[rankIndex].name()
                        + " " + target.getUsername()
        );
    }

    private static void transfer(ServerPlayer player, IServerPartyAPI party,
                                 boolean owner, UUID targetId) {
        if (!owner || party == null || targetId == null) {
            reject(player, "Only the party owner can transfer ownership.");
            return;
        }
        IPartyMemberAPI target = party.getMemberInfo(targetId);
        if (target == null || target.isOwner()) {
            reject(player, "That player cannot become the party owner.");
            return;
        }
        execute(player, "oparties transfer " + target.getUsername() + " confirm");
    }

    private static void leave(ServerPlayer player, IServerPartyAPI party,
                              boolean owner) {
        if (party == null || owner) {
            reject(player, "The party owner must disband or transfer the party.");
            return;
        }
        if (execute(player, "oparties leave") > 0) {
            PartyChatSavedData.get(player.server)
                    .setEnabled(player.getUUID(), false);
        }
    }

    private static void disband(ServerPlayer player, IServerPartyAPI party,
                                boolean owner) {
        if (!owner || party == null) {
            reject(player, "Only the party owner can disband the party.");
            return;
        }
        List<UUID> members = party.getMemberInfoStream()
                .map(IPartyMemberAPI::getUUID)
                .toList();
        if (execute(player, "oparties destroy confirm") > 0) {
            PartyChatSavedData data = PartyChatSavedData.get(player.server);
            members.forEach(member -> data.setEnabled(member, false));
        }
    }

    private static int execute(ServerPlayer player, String command) {
        try {
            return player.server.getCommands().getDispatcher()
                    .execute(command, player.createCommandSourceStack());
        } catch (CommandSyntaxException exception) {
            player.createCommandSourceStack().sendFailure(
                    ComponentUtils.fromMessage(exception.getRawMessage())
            );
            return 0;
        }
    }

    private static boolean hasRank(IPartyMemberAPI member,
                                   PartyMemberRank rank) {
        return member != null && member.getRank().ordinal() >= rank.ordinal();
    }

    private static int rankOrdinal(String rank) {
        try {
            return PartyMemberRank.valueOf(rank.toUpperCase(Locale.ROOT)).ordinal();
        } catch (IllegalArgumentException exception) {
            return 0;
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

    private static PartyMenuStatePayload.MemberEntry memberEntry(
            ServerPlayer viewer, IPartyMemberAPI member) {
        return new PartyMenuStatePayload.MemberEntry(
                member.getUUID(),
                member.getUsername(),
                member.isOwner() ? "OWNER" : member.getRank().name(),
                member.isOwner(),
                viewer.server.getPlayerList().getPlayer(member.getUUID()) != null
        );
    }

    private static PartyMenuStatePayload emptySnapshot(boolean openScreen) {
        return new PartyMenuStatePayload(
                false,
                openScreen,
                "",
                "",
                false,
                "",
                false,
                false,
                false,
                0,
                List.of(),
                List.of()
        );
    }

    private static void reject(ServerPlayer player, String message) {
        LOGGER.debug(
                "Rejected party menu action from {}: {}",
                player.getGameProfile().getName(),
                message
        );
        send(player, message, ChatFormatting.RED);
    }

    private static boolean allowRequest(Map<UUID, Long> requests,
                                        UUID playerId,
                                        long interval) {
        long now = System.nanoTime();
        long next = requests.getOrDefault(playerId, 0L);
        if (now < next) {
            return false;
        }
        requests.put(playerId, now + interval);
        if (requests.size() > 1024) {
            requests.entrySet().removeIf(entry -> entry.getValue() < now);
        }
        return true;
    }

    private static void send(ServerPlayer player, String message,
                             ChatFormatting color) {
        player.sendSystemMessage(Component.literal(message).withStyle(color));
    }
}
