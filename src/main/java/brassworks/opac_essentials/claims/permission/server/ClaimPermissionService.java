package brassworks.opac_essentials.claims.permission.server;

import brassworks.opac_essentials.claims.permission.model.ClaimPermissionAction;
import brassworks.opac_essentials.claims.permission.model.ClaimPermissionTarget;
import brassworks.opac_essentials.compat.openpac.OpenPacCompat;
import brassworks.opac_essentials.config.EssentialsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import xaero.pac.common.server.core.accessor.ICreateContraption;
import xaero.pac.common.server.core.accessor.ICreateContraptionEntity;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ClaimPermissionService {
    private static final String TRAIN_ENTITY_CLASS =
            "com.simibubi.create.content.trains.entity.CarriageContraptionEntity";
    private static final ResourceLocation TRAIN_CONTROLS_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("create", "controls");
    private static final int GLOBAL_SUB_CONFIG_INDEX = -1;

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
        if (action == ClaimPermissionAction.INTERACT && isTrainEntity(target)) {
            return Boolean.TRUE.equals(trainInteractionPermission(target, player));
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

    @Nullable
    public static Boolean trainInteractionPermission(int entityId, ServerPlayer player) {
        if (!ModList.get().isLoaded("create")) {
            return null;
        }
        Entity train = player.serverLevel().getEntity(entityId);
        if (!isTrainEntity(train)) {
            return null;
        }
        return trainInteractionPermission(train, player);
    }

    public static boolean isTrainController(int entityId, @Nullable BlockPos localPos,
                                            ServerPlayer player) {
        if (!ModList.get().isLoaded("create")) {
            return false;
        }
        Entity train = player.serverLevel().getEntity(entityId);
        return isTrainEntity(train) && isTrainControls(train, localPos);
    }

    @Nullable
    public static Boolean trainControlPermission(int entityId, ServerPlayer player) {
        if (!ModList.get().isLoaded("create")) {
            return null;
        }
        Entity train = player.serverLevel().getEntity(entityId);
        if (!isTrainEntity(train)) {
            return null;
        }
        return trainControlPermission(train, player);
    }

    @Nullable
    public static Boolean trainOperationPermission(int entityId, ServerPlayer player) {
        if (!ModList.get().isLoaded("create")) {
            return null;
        }
        Entity train = player.serverLevel().getEntity(entityId);
        if (!isTrainEntity(train)) {
            return null;
        }
        Boolean travelPermission = trainTravelPermission(train, player);
        if (Boolean.FALSE.equals(travelPermission)) {
            return OpenPacCompat.isClaimsAdminMode(player);
        }
        return withTrainAdminBypass(
                trainOwnerPermission(train, player, ClaimPermissionAction.CONTROL),
                player
        );
    }

    @Nullable
    private static Boolean trainControlPermission(Entity train, ServerPlayer player) {
        return withTrainAdminBypass(
                trainOwnerPermission(train, player, ClaimPermissionAction.CONTROL),
                player
        );
    }

    @Nullable
    private static Boolean trainInteractionPermission(Entity train, ServerPlayer player) {
        return withTrainAdminBypass(
                trainOwnerPermission(train, player, ClaimPermissionAction.INTERACT),
                player
        );
    }

    @Nullable
    private static Boolean withTrainAdminBypass(@Nullable Boolean allowed,
                                                ServerPlayer player) {
        if (Boolean.FALSE.equals(allowed) && OpenPacCompat.isClaimsAdminMode(player)) {
            return true;
        }
        return allowed;
    }

    @Nullable
    private static Boolean trainOwnerPermission(Entity train, ServerPlayer player,
                                                ClaimPermissionAction action) {
        UUID trainOwner = trainOwner(train);
        if (trainOwner != null) {
            if (player.getUUID().equals(trainOwner)) {
                return true;
            }
            if (ClaimPermissionsSavedData.get(player.getServer()).allows(
                    trainOwner,
                    GLOBAL_SUB_CONFIG_INDEX,
                    ClaimPermissionTarget.TRAIN,
                    ClaimPermissionTarget.TRAIN_TARGET_ID,
                    action,
                    player.getUUID()
            )) {
                return true;
            }
        }
        return EssentialsConfig.PROTECT_TRAIN_CONTROLS.get() ? false : null;
    }

    @Nullable
    private static Boolean trainTravelPermission(Entity train, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        OpenPacCompat.Claim claim = OpenPacCompat.getClaimAt(
                player.getServer(),
                level.dimension().location(),
                new ChunkPos(train.blockPosition())
        );
        if (claim == null) {
            return null;
        }
        UUID trainOwner = trainOwner(train);
        if (trainOwner == null) {
            return false;
        }
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(player.getServer());
        return claim.ownerId().equals(trainOwner)
                || data.isTrusted(
                        claim.ownerId(),
                        claim.subConfigIndex(),
                        trainOwner
                )
                || data.allows(
                        claim.ownerId(),
                        claim.subConfigIndex(),
                        ClaimPermissionTarget.TRAIN,
                        ClaimPermissionTarget.TRAIN_TARGET_ID,
                        ClaimPermissionAction.TRAVEL,
                        trainOwner
                );
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
        ClaimPermissionsSavedData data = ClaimPermissionsSavedData.get(player.getServer());
        return data.isTrusted(
                claim.ownerId(),
                claim.subConfigIndex(),
                player.getUUID()
        ) || data.allows(
                claim.ownerId(),
                claim.subConfigIndex(),
                target,
                targetId,
                action,
                player.getUUID()
        );
    }

    private static boolean isTrainEntity(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        Class<?> type = entity.getClass();
        while (type != null) {
            if (TRAIN_ENTITY_CLASS.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean isTrainControls(Entity train, @Nullable BlockPos localPos) {
        if (localPos == null || !(train instanceof ICreateContraptionEntity accessor)) {
            return false;
        }
        ICreateContraption contraption = accessor.getXaero_OPAC_contraption();
        if (contraption == null) {
            return false;
        }
        StructureBlockInfo block = contraption.getBlocks().get(localPos);
        return block != null && TRAIN_CONTROLS_BLOCK_ID.equals(
                BuiltInRegistries.BLOCK.getKey(block.state().getBlock())
        );
    }

    @Nullable
    private static UUID trainOwner(@Nullable Entity entity) {
        if (!isTrainEntity(entity)) {
            return null;
        }
        try {
            Method getCarriage = entity.getClass().getMethod("getCarriage");
            Object carriage = getCarriage.invoke(entity);
            Object train = carriage.getClass().getField("train").get(carriage);
            Object owner = train.getClass().getField("owner").get(train);
            return owner instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
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
