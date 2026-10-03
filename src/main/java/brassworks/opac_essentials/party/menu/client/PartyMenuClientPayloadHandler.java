package brassworks.opac_essentials.party.menu.client;

import brassworks.opac_essentials.party.menu.network.PartyMenuStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.swzo.brass.ui.BrassScreen;

public final class PartyMenuClientPayloadHandler {
    private static Screen pendingParent;

    private PartyMenuClientPayloadHandler() {
    }

    public static void requestOpen(Screen parent) {
        pendingParent = parent;
    }

    public static void handle(PartyMenuStatePayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof PartyManagementScreen screen) {
                screen.applySnapshot(payload);
                return;
            }
            if (!payload.openScreen()) {
                return;
            }
            Screen parent = pendingParent;
            pendingParent = null;
            if (parent != null && minecraft.screen != parent) {
                return;
            }
            if (!payload.hasParty()
                    && parent instanceof BrassScreen screen) {
                PartyManagementScreen.showCreationPrompt(
                        screen.getBackground(),
                        () -> {
                        }
                );
                return;
            }
            minecraft.setScreen(new PartyManagementScreen(payload));
        });
    }
}
