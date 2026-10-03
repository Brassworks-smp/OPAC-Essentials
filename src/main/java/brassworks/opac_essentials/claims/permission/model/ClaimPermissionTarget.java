package brassworks.opac_essentials.claims.permission.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public enum ClaimPermissionTarget {
    BLOCK("block"),
    ENTITY("entity"),
    TRAIN("train"),
    THROWABLE("throwable"),
    BLOCK_ENTITY("block-entity");

    public static final ResourceLocation TRAIN_TARGET_ID =
            ResourceLocation.fromNamespaceAndPath("create", "carriage_contraption");

    private final String commandName;
    private volatile List<ResourceLocation> registeredIds = List.of();

    ClaimPermissionTarget(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public boolean supports(ClaimPermissionAction action) {
        return switch (this) {
            case BLOCK -> action == ClaimPermissionAction.INTERACT
                    || action == ClaimPermissionAction.BREAK
                    || action == ClaimPermissionAction.PLACE;
            case BLOCK_ENTITY -> action == ClaimPermissionAction.INTERACT
                    || action == ClaimPermissionAction.BREAK;
            case ENTITY -> action == ClaimPermissionAction.INTERACT
                    || action == ClaimPermissionAction.ATTACK;
            case TRAIN -> action == ClaimPermissionAction.TRAVEL
                    || action == ClaimPermissionAction.INTERACT
                    || action == ClaimPermissionAction.CONTROL;
            case THROWABLE -> action == ClaimPermissionAction.THROWABLE;
        };
    }

    public boolean isRegistered(ResourceLocation id) {
        return switch (this) {
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case TRAIN -> isCreateLoaded()
                    && TRAIN_TARGET_ID.equals(id)
                    && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case THROWABLE -> BuiltInRegistries.ITEM.containsKey(id)
                    || BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case BLOCK_ENTITY -> BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id);
        };
    }

    public Stream<ResourceLocation> registeredIds() {
        return registeredIds.stream();
    }

    public static void prepareRegisteredIds() {
        BLOCK.registeredIds = List.copyOf(BuiltInRegistries.BLOCK.keySet());
        ENTITY.registeredIds = List.copyOf(BuiltInRegistries.ENTITY_TYPE.keySet());
        TRAIN.registeredIds = TRAIN.isRegistered(TRAIN_TARGET_ID)
                ? List.of(TRAIN_TARGET_ID)
                : List.of();
        Set<ResourceLocation> throwableIds = new LinkedHashSet<>(
                BuiltInRegistries.ITEM.keySet()
        );
        throwableIds.addAll(BuiltInRegistries.ENTITY_TYPE.keySet());
        THROWABLE.registeredIds = List.copyOf(throwableIds);
        BLOCK_ENTITY.registeredIds = List.copyOf(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()
        );
    }

    public static List<ClaimPermissionTarget> availableTargets() {
        return Stream.of(values())
                .filter(target -> target != TRAIN || isCreateLoaded())
                .toList();
    }

    private static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }
}
