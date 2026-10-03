package brassworks.opac_essentials.claims.menu.client;

import brassworks.opac_essentials.opac_essentials;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = opac_essentials.MODID, value = Dist.CLIENT)
public final class OpacMainMenuReplacement {
    private static final String OPAC_MAIN_MENU = "xaero.pac.client.gui.MainMenu";

    private OpacMainMenuReplacement() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen != null && OPAC_MAIN_MENU.equals(screen.getClass().getName())) {
            event.setNewScreen(new OpacMainMenuScreen(screen, event.getCurrentScreen()));
        }
    }
}
