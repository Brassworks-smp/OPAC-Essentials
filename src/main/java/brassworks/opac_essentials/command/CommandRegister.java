package brassworks.opac_essentials.command;

import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget;
import brassworks.opac_essentials.config.EssentialsConfig;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

public final class CommandRegister {
    private static final Logger LOGGER = LogUtils.getLogger();

    public void register(CommandDispatcher<CommandSourceStack> dispatcher,
                         Commands.CommandSelection commandSelection) {
        ClaimPermissionTarget.prepareRegisteredIds();

        Set<String> configuredNames = new HashSet<>();
        String claims = reserve(
                dispatcher, configuredNames, EssentialsConfig.CLAIMS_COMMAND.get(), "claims"
        );
        String party = reserve(
                dispatcher, configuredNames, EssentialsConfig.PARTY_COMMAND.get(), "party"
        );
        String claim = reserve(
                dispatcher, configuredNames, EssentialsConfig.CLAIM_COMMAND.get(), "claim"
        );
        String unclaim = reserve(
                dispatcher, configuredNames, EssentialsConfig.UNCLAIM_COMMAND.get(), "unclaim"
        );
        String partyChat = reserve(
                dispatcher, configuredNames, EssentialsConfig.PARTY_CHAT_COMMAND.get(), "party chat"
        );

        if (partyChat != null) {
            new PartyChatCommand(partyChat).register(dispatcher);
        }
        if (claims != null) {
            new OpenClaimsCommand(claims).register(dispatcher);
        }
        if (party != null) {
            new OpenPartiesCommand(party).register(dispatcher);
        }
        new QuickClaimCommands(claim, unclaim).register(dispatcher);
    }

    private String reserve(CommandDispatcher<CommandSourceStack> dispatcher,
                           Set<String> configuredNames, String commandName,
                           String featureName) {
        if (!configuredNames.add(commandName)) {
            LOGGER.error(
                    "[OPAC Essentials] Cannot register the {} command as /{}: "
                            + "that name is used by another configured command.",
                    featureName, commandName
            );
            return null;
        }
        if (dispatcher.getRoot().getChild(commandName) != null) {
            LOGGER.error(
                    "[OPAC Essentials] Cannot register the {} command as /{}: "
                            + "another mod already registered that command.",
                    featureName, commandName
            );
            return null;
        }
        return commandName;
    }
}
