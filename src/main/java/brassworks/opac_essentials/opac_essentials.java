package brassworks.opac_essentials;

import brassworks.opac_essentials.command.CommandRegister;
import brassworks.opac_essentials.config.CommandConfig;
import brassworks.opac_essentials.partychat.PartyChatListener;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(opac_essentials.MODID)
public final class opac_essentials {
    public static final String MODID = "opac_essentials";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> PARTY_CHAT_ENABLED = ConcurrentHashMap.newKeySet();

    public opac_essentials(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                CommandConfig.SPEC,
                "opac_essentials-commands.toml"
        );
        modEventBus.addListener(ClaimPermissionsNetwork::register);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new PartyChatListener());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRegisterCommands(RegisterCommandsEvent event) {
        new CommandRegister().register(
                event.getDispatcher(), event.getCommandSelection()
        );
        LOGGER.info("[OPAC Essentials] Registered commands");
    }

    public static boolean togglePartyChat(UUID playerId) {
        if (PARTY_CHAT_ENABLED.remove(playerId)) {
            return false;
        }
        PARTY_CHAT_ENABLED.add(playerId);
        return true;
    }

    public static boolean isPartyChatEnabled(UUID playerId) {
        return PARTY_CHAT_ENABLED.contains(playerId);
    }

    public static void disablePartyChat(UUID playerId) {
        PARTY_CHAT_ENABLED.remove(playerId);
    }
}
