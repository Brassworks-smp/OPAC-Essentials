package brassworks.opac_essentials.mixin;

import brassworks.opac_essentials.permission.ClaimPermissionAction;
import brassworks.opac_essentials.permission.ClaimPermissionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "xaero.pac.common.event.CommonEventsNeoForge",
        priority = 2000, remap = false)
public abstract class OpenPacEventsMixin {
    @Inject(method = "onEntityPlaceBlock", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowPlace(BlockEvent.EntityPlaceEvent event,
                                           CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level
                && ClaimPermissionService.allowsBlockPlacement(
                level, event.getPos(), event.getEntity(), event.getPlacedBlock()
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onEntityMultiPlaceBlock", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowMultiPlace(BlockEvent.EntityMultiPlaceEvent event,
                                                CallbackInfo callback) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean allAllowed = event.getReplacedBlockSnapshots().stream().allMatch(snapshot ->
                ClaimPermissionService.allowsBlockPlacement(
                        level,
                        snapshot.getPos(),
                        event.getEntity(),
                        snapshot.getCurrentState()
                )
        );
        if (allAllowed) {
            callback.cancel();
        }
    }

    @Inject(method = "onLeftClickBlock", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowBreakStart(
            PlayerInteractEvent.LeftClickBlock event, CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level
                && ClaimPermissionService.allowsBlockAction(
                level,
                event.getPos(),
                level.getBlockState(event.getPos()),
                event.getEntity(),
                ClaimPermissionAction.BREAK
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowBreak(BlockEvent.BreakEvent event,
                                           CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level
                && ClaimPermissionService.allowsBlockAction(
                level,
                event.getPos(),
                event.getState(),
                event.getPlayer(),
                ClaimPermissionAction.BREAK
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowBlockInteraction(
            PlayerInteractEvent.RightClickBlock event, CallbackInfo callback) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean interact = ClaimPermissionService.allowsBlockAction(
                level,
                event.getPos(),
                level.getBlockState(event.getPos()),
                event.getEntity(),
                ClaimPermissionAction.INTERACT
        );
        boolean place = ClaimPermissionService.allowsBlockPlacement(
                level,
                event.getEntity(),
                event.getHand(),
                event.getItemStack(),
                event.getHitVec()
        );
        boolean throwable = ClaimPermissionService.allowsThrowableUse(
                level,
                event.getPos(),
                event.getEntity(),
                event.getItemStack()
        );
        if (!interact && !place && !throwable) {
            return;
        }
        event.setUseBlock(interact ? TriState.TRUE : TriState.FALSE);
        event.setUseItem(interact || place || throwable ? TriState.TRUE : TriState.FALSE);
        callback.cancel();
    }

    @Inject(method = "onItemRightClick", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void opacEssentials$allowThrowableUse(
            PlayerInteractEvent.RightClickItem event, CallbackInfo callback) {
        if (event.getLevel() instanceof ServerLevel level
                && ClaimPermissionService.allowsThrowableUse(
                level,
                event.getPos(),
                event.getEntity(),
                event.getItemStack()
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onEntityAttack", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowEntityAttack(AttackEntityEvent event,
                                                  CallbackInfo callback) {
        if (ClaimPermissionService.allowsEntityAction(
                event.getEntity(), event.getTarget(), ClaimPermissionAction.ATTACK
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowEntityDamage(LivingIncomingDamageEvent event,
                                                  CallbackInfo callback) {
        if (ClaimPermissionService.allowsEntityDamage(
                event.getSource().getEntity(),
                event.getEntity()
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onProjectileImpact", at = @At("HEAD"), cancellable = true,
            require = 0)
    private void opacEssentials$allowThrowableImpact(
            ProjectileImpactEvent event, CallbackInfo callback) {
        Projectile projectile = event.getProjectile();
        BlockPos impactPos = BlockPos.containing(event.getRayTraceResult().getLocation());
        if (ClaimPermissionService.allowsThrowableImpact(projectile, impactPos)) {
            callback.cancel();
        }
    }

    @Inject(method = "onEntityInteract", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowEntityInteract(
            PlayerInteractEvent.EntityInteract event, CallbackInfo callback) {
        if (allowsEntityInteraction(event)) {
            callback.cancel();
        }
    }

    @Inject(method = "onInteractEntitySpecific", at = @At("HEAD"), cancellable = true)
    private void opacEssentials$allowSpecificEntityInteract(
            PlayerInteractEvent.EntityInteractSpecific event, CallbackInfo callback) {
        if (allowsEntityInteraction(event)) {
            callback.cancel();
        }
    }

    private boolean allowsEntityInteraction(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.EntityInteract entityEvent) {
            return ClaimPermissionService.allowsEntityAction(
                    entityEvent.getEntity(),
                    entityEvent.getTarget(),
                    ClaimPermissionAction.INTERACT
            );
        }
        PlayerInteractEvent.EntityInteractSpecific entityEvent =
                (PlayerInteractEvent.EntityInteractSpecific) event;
        return ClaimPermissionService.allowsEntityAction(
                entityEvent.getEntity(),
                entityEvent.getTarget(),
                ClaimPermissionAction.INTERACT
        );
    }
}
