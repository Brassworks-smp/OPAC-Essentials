package brassworks.opac_essentials.claims.permission.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record ClaimPermissionKey(
        UUID claimOwner,
        int subConfigIndex,
        ClaimPermissionTarget target,
        String targetId,
        ClaimPermissionAction action,
        @Nullable UUID player,
        boolean allowed
) {
    public ClaimPermissionKey(UUID claimOwner, int subConfigIndex,
                              ClaimPermissionTarget target, ResourceLocation targetId,
                              ClaimPermissionAction action, @Nullable UUID player) {
        this(claimOwner, subConfigIndex, target, targetId, action, player, true);
    }

    public ClaimPermissionKey(UUID claimOwner, int subConfigIndex,
                              ClaimPermissionTarget target, ResourceLocation targetId,
                              ClaimPermissionAction action, @Nullable UUID player,
                              boolean allowed) {
        this(claimOwner, subConfigIndex, target, targetId.toString(), action, player, allowed);
    }

    public ClaimPermissionKey(UUID claimOwner, int subConfigIndex,
                              ClaimPermissionTarget target, String targetId,
                              ClaimPermissionAction action, @Nullable UUID player) {
        this(claimOwner, subConfigIndex, target, targetId, action, player, true);
    }

    public ClaimPermissionKey {
        Objects.requireNonNull(claimOwner, "claimOwner");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
    }

    public ClaimPermissionKey withAllowed(boolean allowed) {
        return new ClaimPermissionKey(
                claimOwner, subConfigIndex, target, targetId, action, player, allowed
        );
    }
}
