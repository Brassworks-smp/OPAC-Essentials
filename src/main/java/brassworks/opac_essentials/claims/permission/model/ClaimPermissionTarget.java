package brassworks.opac_essentials.claims.permission.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public enum ClaimPermissionTarget {
    BLOCK("block"),
    ENTITY("entity"),
    THROWABLE("throwable"),
    BLOCK_ENTITY("block-entity");

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
            case THROWABLE -> action == ClaimPermissionAction.THROWABLE;
        };
    }

    public boolean isRegistered(ResourceLocation id) {
        return switch (this) {
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
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
        Set<ResourceLocation> throwableIds = new LinkedHashSet<>(
                BuiltInRegistries.ITEM.keySet()
        );
        throwableIds.addAll(BuiltInRegistries.ENTITY_TYPE.keySet());
        THROWABLE.registeredIds = List.copyOf(throwableIds);
        BLOCK_ENTITY.registeredIds = List.copyOf(
                BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()
        );
    }
}
