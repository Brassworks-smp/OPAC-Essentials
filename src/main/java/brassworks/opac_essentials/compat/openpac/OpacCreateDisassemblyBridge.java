package brassworks.opac_essentials.compat.openpac;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OpacCreateDisassemblyBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONTRAPTION_ENTITY =
            "com.simibubi.create.content.contraptions.AbstractContraptionEntity";
    private static final String CONTRAPTION = "com.simibubi.create.content.contraptions.Contraption";
    private static final String STRUCTURE_TRANSFORM = "com.simibubi.create.content.contraptions.StructureTransform";
    private static final String SERVER_CORE = "xaero.pac.common.server.core.ServerCore";
    private static final String CONTRAPTION_ACCESSOR =
            "xaero.pac.common.server.core.accessor.ICreateContraption";

    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static volatile boolean resolved;
    private static Field contraptionField;
    private static Method makeStructureTransform;
    private static Method getControllingPlayer;
    private static Method getBlocks;
    private static Method applyTransform;
    private static Class<?> accessorClass;
    private static Method isCreateModAllowed;
    private static Throwable resolutionError;

    private OpacCreateDisassemblyBridge() {
    }

    public static Result check(Level level, Object entity) {
        resolve();
        if (resolutionError != null) {
            return Result.blocked();
        }

        try {
            Object contraption = contraptionField.get(entity);
            if (contraption == null) {
                return Result.allow();
            }
            if (!accessorClass.isInstance(contraption)) {
                return bridgeFailure(new IllegalStateException(
                        "OPAC did not apply ICreateContraption to "
                                + contraption.getClass().getName()
                ));
            }

            Object transform = makeStructureTransform.invoke(entity);
            Object blockMap = getBlocks.invoke(contraption);
            if (!(blockMap instanceof Map<?, ?> blocks)) {
                return bridgeFailure(new IllegalStateException(
                        "Create Contraption#getBlocks did not return a map"
                ));
            }

            for (Object block : blocks.values()) {
                if (!(block instanceof StructureBlockInfo info)) {
                    return bridgeFailure(new IllegalStateException(
                            "Unexpected Create structure block type: "
                                    + (block == null ? "null" : block.getClass().getName())
                    ));
                }
                BlockPos targetPos = (BlockPos) applyTransform.invoke(transform, info.pos());
                boolean allowed = (boolean) isCreateModAllowed.invoke(
                        null, level, targetPos, contraption
                );
                if (!allowed) {
                    return Result.deniedAt(targetPos);
                }
            }
            return Result.allow();
        } catch (IllegalAccessException | InvocationTargetException
                 | RuntimeException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    && invocation.getCause() != null
                    ? invocation.getCause()
                    : exception;
            return bridgeFailure(cause);
        }
    }

    public static Optional<UUID> controllingPlayer(Object entity) {
        resolve();
        if (resolutionError != null) {
            return Optional.empty();
        }
        try {
            Object result = getControllingPlayer.invoke(entity);
            if (result instanceof Optional<?> optional && optional.orElse(null) instanceof UUID uuid) {
                return Optional.of(uuid);
            }
        } catch (IllegalAccessException | InvocationTargetException
                 | RuntimeException | LinkageError exception) {
            reportFailure(exception);
        }
        return Optional.empty();
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (OpacCreateDisassemblyBridge.class) {
            if (resolved) {
                return;
            }
            try {
                ClassLoader loader = OpacCreateDisassemblyBridge.class.getClassLoader();
                Class<?> entityClass = Class.forName(CONTRAPTION_ENTITY, false, loader);
                Class<?> contraptionClass = Class.forName(CONTRAPTION, false, loader);
                Class<?> transformClass = Class.forName(STRUCTURE_TRANSFORM, false, loader);
                Class<?> serverCoreClass = Class.forName(SERVER_CORE, false, loader);
                accessorClass = Class.forName(CONTRAPTION_ACCESSOR, false, loader);

                contraptionField = entityClass.getDeclaredField("contraption");
                contraptionField.setAccessible(true);
                makeStructureTransform = entityClass.getDeclaredMethod("makeStructureTransform");
                makeStructureTransform.setAccessible(true);
                getControllingPlayer = entityClass.getMethod("getControllingPlayer");
                getBlocks = contraptionClass.getMethod("getBlocks");
                applyTransform = transformClass.getMethod("apply", BlockPos.class);
                isCreateModAllowed = serverCoreClass.getMethod(
                        "isCreateModAllowed", Level.class, BlockPos.class, accessorClass
                );
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                resolutionError = exception;
                reportFailure(exception);
            } finally {
                resolved = true;
            }
        }
    }

    private static Result bridgeFailure(Throwable error) {
        reportFailure(error);
        return Result.blocked();
    }

    private static void reportFailure(Throwable error) {
        if (REPORTED_FAILURE.compareAndSet(false, true)) {
            LOGGER.error("[OPAC Essentials] Create disassembly protection preflight failed.", error);
        }
    }

    public record Result(boolean allowed, BlockPos deniedPos) {
        private static Result allow() {
            return new Result(true, null);
        }

        private static Result deniedAt(BlockPos pos) {
            return new Result(false, pos.immutable());
        }

        private static Result blocked() {
            return new Result(false, null);
        }
    }
}
