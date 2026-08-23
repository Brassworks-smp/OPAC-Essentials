package brassworks.opac_essentials.claims.permission.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record ClaimPermissionKey(
        UUID claimOwner,
        int subConfigIndex,
        ClaimPermissionTarget target,
        ResourceLocation targetId,
        ClaimPermissionAction action,
        @Nullable UUID player
) {
    public ClaimPermissionKey {
        Objects.requireNonNull(claimOwner, "claimOwner");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
    }
}
