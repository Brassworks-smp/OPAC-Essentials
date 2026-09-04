package brassworks.opac_essentials.mixin.common;

import brassworks.opac_essentials.claims.permission.server.ClaimPermissionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "xaero.pac.common.server.core.ServerCore", priority = 2000, remap = false)
public abstract class OpenPacCreatePermissionsMixin {
    @Inject(
            method = "isCreateContraptionInteractionPacketAllowed(ILnet/minecraft/world/InteractionHand;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void opacEssentials$checkTrainEntityInteraction(
            int entityId, InteractionHand hand, ServerPlayer player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (ClaimPermissionService.allowsTrainInteraction(entityId, player)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(
            method = "isCreateContraptionInteractionPacketAllowed(ILnet/minecraft/world/InteractionHand;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void opacEssentials$checkTrainInteraction(
            int entityId, InteractionHand hand, BlockPos localPos, ServerPlayer player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (ClaimPermissionService.isTrainController(entityId, localPos, player)) {
            Boolean allowed = ClaimPermissionService.trainControlPermission(entityId, player);
            if (allowed != null) {
                callback.setReturnValue(allowed);
            }
            return;
        }
        if (ClaimPermissionService.allowsTrainInteraction(entityId, player)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(
            method = "isCreateContraptionControlsPacketAllowed(ILnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void opacEssentials$checkContraptionControls(
            int entityId, ServerPlayer player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        Boolean allowed = ClaimPermissionService.trainOperationPermission(entityId, player);
        if (allowed != null) {
            callback.setReturnValue(allowed);
        }
    }

    @Inject(
            method = "isCreateTrainControlsPacketAllowed(ILnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void opacEssentials$checkTrainControls(
            int entityId, ServerPlayer player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        Boolean allowed = ClaimPermissionService.trainOperationPermission(entityId, player);
        if (allowed != null) {
            callback.setReturnValue(allowed);
        }
    }
}
