package brassworks.opac_essentials.permission;

import brassworks.opac_essentials.compat.OpenPacCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class ClaimPermissionService {
    private ClaimPermissionService() {
    }

    public static boolean allowsBlockAction(ServerLevel level, BlockPos pos,
                                            BlockState blockState, Entity source,
                                            ClaimPermissionAction action) {
        ServerPlayer player = findActingPlayer(source);
        if (player == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        if (allows(level, pos, player, ClaimPermissionTarget.BLOCK, blockId, action)) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && allows(
                level,
                pos,
                player,
                ClaimPermissionTarget.BLOCK_ENTITY,
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()),
                action
        );
    }

    public static boolean allowsBlockPlacement(ServerLevel level, BlockPos pos, Entity source,
                                               BlockState placedState) {
        ServerPlayer player = findActingPlayer(source);
        if (player == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(placedState.getBlock());
        return allows(level, pos, player, ClaimPermissionTarget.BLOCK, blockId,
                ClaimPermissionAction.PLACE);
    }

    public static boolean allowsBlockPlacement(ServerLevel level, Entity source,
                                               InteractionHand hand, ItemStack heldItem,
                                               BlockHitResult hitResult) {
        if (!(heldItem.getItem() instanceof BlockItem blockItem)
                || !(source instanceof ServerPlayer player)) {
            return false;
        }
        BlockPlaceContext context = new BlockPlaceContext(player, hand, heldItem, hitResult);
        return allowsBlockPlacement(
                level,
                context.getClickedPos(),
                player,
                blockItem.getBlock().defaultBlockState()
        );
    }

    public static boolean allowsEntityAction(Entity source, Entity target,
                                             ClaimPermissionAction action) {
        if (!(target.level() instanceof ServerLevel level) || target instanceof ServerPlayer) {
            return false;
        }
        ServerPlayer player = findActingPlayer(source);
        if (player == null) {
            return false;
        }
        if (target instanceof Projectile projectile) {
            return allowsThrowableProjectile(level, target.blockPosition(), player, projectile);
        }
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        return allows(level, target.blockPosition(), player,
                ClaimPermissionTarget.ENTITY, targetId, action);
    }

    public static boolean allowsEntityDamage(Entity source, Entity target) {
        if (!(target.level() instanceof ServerLevel level) || target instanceof ServerPlayer) {
            return false;
        }
        ServerPlayer player = findActingPlayer(source);
        if (player == null) {
            return false;
        }
        if (source instanceof Projectile projectile) {
            return allowsThrowableProjectile(level, target.blockPosition(), player, projectile);
        }
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        return allows(level, target.blockPosition(), player,
                ClaimPermissionTarget.ENTITY, targetId, ClaimPermissionAction.ATTACK);
    }

    public static boolean allowsThrowableUse(ServerLevel level, BlockPos pos, Entity source,
                                             ItemStack itemStack) {
        ServerPlayer player = findActingPlayer(source);
        if (player == null || itemStack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return allows(level, pos, player, ClaimPermissionTarget.THROWABLE, itemId,
                ClaimPermissionAction.THROWABLE);
    }

    public static boolean allowsThrowableImpact(Projectile projectile, BlockPos pos) {
        if (!(projectile.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerPlayer player = findActingPlayer(projectile);
        if (player == null) {
            return false;
        }
        return allowsThrowableProjectile(level, pos, player, projectile);
    }

    private static boolean allowsThrowableProjectile(ServerLevel level, BlockPos pos,
                                                      ServerPlayer player,
                                                      Projectile projectile) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (allows(level, pos, player, ClaimPermissionTarget.THROWABLE, entityId,
                ClaimPermissionAction.THROWABLE)) {
            return true;
        }
        ResourceLocation itemId = projectileItemId(projectile);
        return itemId != null && allows(
                level, pos, player, ClaimPermissionTarget.THROWABLE, itemId,
                ClaimPermissionAction.THROWABLE
        );
    }

    @Nullable
    private static ResourceLocation projectileItemId(Projectile projectile) {
        try {
            Method getItem = projectile.getClass().getMethod("getItem");
            Object result = getItem.invoke(projectile);
            if (result instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                return BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static boolean allows(ServerLevel level, BlockPos pos, ServerPlayer player,
                                  ClaimPermissionTarget target, ResourceLocation targetId,
                                  ClaimPermissionAction action) {
        OpenPacCompat.Claim claim = OpenPacCompat.getClaimAt(
                level.getServer(), level.dimension().location(), new ChunkPos(pos)
        );
        if (claim == null) {
            return false;
        }
        return ClaimPermissionsSavedData.get(player.getServer()).allows(
                claim.ownerId(),
                claim.subConfigIndex(),
                target,
                targetId,
                action,
                player.getUUID()
        );
    }

    @Nullable
    private static ServerPlayer findActingPlayer(@Nullable Entity source) {
        if (source instanceof ServerPlayer player) {
            return player;
        }
        if (source instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
