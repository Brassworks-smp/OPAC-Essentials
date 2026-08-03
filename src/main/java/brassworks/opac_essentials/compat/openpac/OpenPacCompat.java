package brassworks.opac_essentials.compat.openpac;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class OpenPacCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SERVER_API =
            "xaero.pac.common.server.api.OpenPACServerAPI";
    private static final String V2_MARKER =
            "xaero.pac.common.event.api.v2.OPACServerAddonRegisterEvent";
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    private OpenPacCompat() {
    }

    public static int detectedApiVersion() {
        try {
            Class.forName(V2_MARKER, false, OpenPacCompat.class.getClassLoader());
            return 2;
        } catch (ClassNotFoundException ignored) {
            return 1;
        }
    }

    @Nullable
    public static Claim getClaimAt(MinecraftServer server, ResourceLocation dimension,
                                   ChunkPos chunkPos) {
        try {
            Object api = getServerApi(server);
            Object manager = invokeFirst(api,
                    new String[]{"getServerClaimsManager", "getClaimsManager"});
            Object claim = invoke(manager, "get", dimension, chunkPos);
            if (claim == null) {
                return null;
            }
            UUID ownerId = (UUID) invokeFirst(claim,
                    new String[]{"getPlayerId", "getOwnerId"});
            Number subConfigIndex = (Number) invokeFirst(claim,
                    new String[]{"getSubConfigIndex", "getSubClaimIndex"});
            return new Claim(ownerId, subConfigIndex.intValue());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            reportFailure("claim lookup", exception);
            return null;
        }
    }

    @Nullable
    public static PartyChatAudience getPartyChatAudience(MinecraftServer server,
                                                         UUID senderId) {
        try {
            Object api = getServerApi(server);
            Object partyManager = invokeFirst(api,
                    new String[]{"getPartyManager", "getParties"});
            Object party = invoke(partyManager, "getPartyByMember", senderId);
            if (party == null) {
                return null;
            }

            Object member = invoke(party, "getMemberInfo", senderId);
            if (member == null) {
                return null;
            }
            Object owner = invokeFirst(party, new String[]{"getOwner"});
            Object rank = invokeOptional(member, "getRank");
            String rankName = member.equals(owner)
                    ? "OWNER"
                    : rank == null ? "MEMBER" : rank.toString();
            int rankColor = -1;
            Object color = rank == null ? null : invokeOptional(rank, "getColor");
            if (color instanceof Number number) {
                rankColor = number.intValue();
            }

            List<ServerPlayer> recipients = onlineMembers(server, party);
            return new PartyChatAudience(rankName, rankColor, recipients);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            reportFailure("party lookup", exception);
            return null;
        }
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, Object party)
            throws ReflectiveOperationException {
        Object onlineStream = invokeOptional(party, "getOnlineMemberStream");
        if (onlineStream instanceof Stream<?> stream) {
            try (stream) {
                return stream.filter(ServerPlayer.class::isInstance)
                        .map(ServerPlayer.class::cast)
                        .toList();
            }
        }

        Object memberStream = invokeFirst(party, new String[]{"getMemberInfoStream"});
        List<ServerPlayer> result = new ArrayList<>();
        try (Stream<?> stream = (Stream<?>) memberStream) {
            stream.forEach(member -> {
                UUID playerId = readUuid(member);
                if (playerId == null) {
                    return;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    result.add(player);
                }
            });
        }
        return List.copyOf(result);
    }

    @Nullable
    private static UUID readUuid(Object member) {
        for (String name : new String[]{"getUUID", "getId", "getPlayerId"}) {
            Object value = invokeOptional(member, name);
            if (value instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    private static Object getServerApi(MinecraftServer server)
            throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName(
                SERVER_API, true, OpenPacCompat.class.getClassLoader()
        );
        return invokeStatic(apiClass, "get", server);
    }

    private static Object invokeFirst(Object target, String[] names, Object... arguments)
            throws ReflectiveOperationException {
        ReflectiveOperationException last = null;
        for (String name : names) {
            try {
                return invoke(target, name, arguments);
            } catch (NoSuchMethodException exception) {
                last = exception;
            }
        }
        throw last == null ? new NoSuchMethodException() : last;
    }

    @Nullable
    private static Object invokeOptional(Object target, String name, Object... arguments) {
        try {
            return invoke(target, name, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object invoke(Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, false, arguments);
        return invokeMethod(method, target, arguments);
    }

    @Nullable
    private static Object invokeStatic(Class<?> target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method method = findMethod(target, name, true, arguments);
        return invokeMethod(method, null, arguments);
    }

    private static Method findMethod(Class<?> type, String name, boolean requireStatic,
                                     Object[] arguments) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name)
                    || Modifier.isStatic(method.getModifiers()) != requireStatic
                    || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (arguments[index] != null
                        && !wrap(parameterTypes[index]).isInstance(arguments[index])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    @Nullable
    private static Object invokeMethod(Method method, Object target, Object[] arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            throw new ReflectiveOperationException(cause);
        }
    }

    private static void reportFailure(String operation, Throwable exception) {
        if (REPORTED_FAILURE.compareAndSet(false, true)) {
            LOGGER.error(
                    "[OPAC Essentials] OPAC API V{} compatibility failure during {}.",
                    detectedApiVersion(), operation, exception
            );
        }
    }

    public record Claim(UUID ownerId, int subConfigIndex) {
    }

    public record PartyChatAudience(String rankName, int rankColor,
                                    List<ServerPlayer> recipients) {
    }
}
