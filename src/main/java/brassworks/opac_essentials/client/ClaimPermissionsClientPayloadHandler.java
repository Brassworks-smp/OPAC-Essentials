package brassworks.opac_essentials.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import brassworks.opac_essentials.network.ClaimPermissionsSyncPayload;

public final class ClaimPermissionsClientPayloadHandler {
    private ClaimPermissionsClientPayloadHandler() {
    }

    public static void handle(ClaimPermissionsSyncPayload payload,
                              IPayloadContext context) {
        Minecraft.getInstance().setScreen(new ClaimPermissionsScreen(payload));
    }
}
