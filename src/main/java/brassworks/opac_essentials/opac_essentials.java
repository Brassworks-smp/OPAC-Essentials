package brassworks.opac_essentials;

import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsNetwork;
import brassworks.opac_essentials.command.CommandRegister;
import brassworks.opac_essentials.config.EssentialsConfig;
import brassworks.opac_essentials.partychat.PartyChatListener;
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

@Mod(opac_essentials.MODID)
public final class opac_essentials {
    public static final String MODID = "opac_essentials";
    private static final Logger LOGGER = LogUtils.getLogger();

    public opac_essentials(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                EssentialsConfig.SPEC,
                "opac_essentials.toml"
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

}
