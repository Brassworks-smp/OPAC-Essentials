package brassworks.opac_essentials.claims.permission.client;

import brassworks.opac_essentials.claims.permission.client.screen.ClaimPermissionsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import brassworks.opac_essentials.claims.permission.network.ClaimPermissionsSyncPayload;

public final class ClaimPermissionsClientPayloadHandler {
    private ClaimPermissionsClientPayloadHandler() {
    }

    public static void handle(ClaimPermissionsSyncPayload payload,
                              IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ClaimPermissionsScreen screen) {
            screen.applySync(payload);
            return;
        }
        minecraft.setScreen(new ClaimPermissionsScreen(payload));
    }
}
