package brassworks.opac_essentials.client;

import brassworks.opac_essentials.opac_essentials;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import xaero.pac.client.api.OpenPACClientAPI;
import xaero.pac.client.claims.api.IClientClaimsManagerAPI;
import xaero.pac.client.claims.player.api.IClientPlayerClaimInfoAPI;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = opac_essentials.MODID, value = Dist.CLIENT)
public final class ClaimSearchClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String XAERO_MAP_SCREEN = "xaero.map.gui.GuiMap";
    private static final int SEARCH_WIDTH = 200;
    private static final int SEARCH_HEIGHT = 20;
    private static final int SEARCH_TOP = 28;
    private static final int BUTTON_SIZE = 20;
    private static final int COLOR_NORMAL = 0xE0E0E0;
    private static final int COLOR_ONE_MATCH = 0x55FF55;
    private static final int COLOR_MULTIPLE_MATCHES = 0xFFFF55;
    private static final int COLOR_NO_MATCH = 0xFF5555;
    private static final int SNAPSHOT_CHUNKS_PER_TICK = 4096;
    private static final long SNAPSHOT_TIME_BUDGET_NANOS = 2_000_000L;
    private static final int CLAIM_CHANGE_DEBOUNCE_TICKS = 10;
    private static final AtomicBoolean REPORTED_XAERO_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_SEARCH_FAILURE = new AtomicBoolean();
    private static final Comparator<ClaimCluster> CLUSTER_ORDER = Comparator
            .comparingInt(ClaimCluster::minX)
            .thenComparingInt(ClaimCluster::minZ)
            .thenComparingInt(ClaimCluster::maxX)
            .thenComparingInt(ClaimCluster::maxZ);

    private static Screen activeMapScreen;
    private static EditBox searchBox;
    private static ClusterNavButton previousButton;
    private static ClusterNavButton nextButton;
    private static boolean searchOpen;
    private static String query = "";
    private static List<PlayerMatch> matchingPlayers = List.of();
    private static List<ClaimCluster> clusters = List.of();
    private static ResourceLocation clusterDimension;
    private static UUID selectedPlayerId;
    private static int clusterIndex = -1;
    private static volatile long searchGeneration;
    private static SnapshotJob snapshotJob;
    private static boolean clusterBuildInFlight;
    private static volatile ClusterBuildResult completedClusterBuild;
    private static boolean claimListenerRegistered;
    private static boolean claimsDirty;
    private static int claimChangeDebounce;
    private static volatile SelectedClusterHighlight selectedHighlight =
            new SelectedClusterHighlight(null, Set.of(), 0, 0, -1, -1, 0);

    private ClaimSearchClient() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isXaeroMapScreen(screen)) {
            return;
        }
        ensureClaimListenerRegistered();

        if (activeMapScreen != screen) {
            clearSearch();
            activeMapScreen = screen;
        }

        searchBox = null;
        previousButton = null;
        nextButton = null;

        int buttonTop = findSearchButtonTop(event, screen);
        SearchToggleButton searchButton = new SearchToggleButton(
                screen.width - BUTTON_SIZE,
                buttonTop,
                searchOpen,
                button -> toggleSearch(screen)
        );
        searchButton.setTooltip(Tooltip.create(Component.literal(
                searchOpen ? "Close claim search" : "Open claim search"
        )));
        event.addListener(searchButton);

        if (!searchOpen) {
            return;
        }

        int availableWidth = Math.max(80, screen.width - 80);
        int width = Math.min(SEARCH_WIDTH, availableWidth);
        int left = (screen.width - width) / 2;
        int navigationTop = SEARCH_TOP + SEARCH_HEIGHT + 2;

        previousButton = new ClusterNavButton(
                left,
                navigationTop,
                -1,
                button -> cycleCluster(-1)
        );
        previousButton.setTooltip(Tooltip.create(Component.literal(
                "Previous claim cluster"
        )));
        event.addListener(previousButton);

        nextButton = new ClusterNavButton(
                left + width - BUTTON_SIZE,
                navigationTop,
                1,
                button -> cycleCluster(1)
        );
        nextButton.setTooltip(Tooltip.create(Component.literal(
                "Next claim cluster"
        )));
        event.addListener(nextButton);
        updateNavigationButtons();

        EditBox box = new EditBox(
                Minecraft.getInstance().font,
                left,
                SEARCH_TOP,
                width,
                SEARCH_HEIGHT,
                Component.literal("Claim Search")
        );
        box.setMaxLength(16);
        box.setHint(Component.literal("Search claim owner..."));
        box.setValue(query);
        box.setResponder(ClaimSearchClient::updateQuery);
        searchBox = box;
        updateSearchBoxColor();
        event.addListener(box);
        screen.setFocused(box);
        box.setFocused(true);
    }

    private static int findSearchButtonTop(ScreenEvent.Init.Post event, Screen screen) {
        int topmostRightButton = screen.height;
        for (Object listener : event.getListenersList()) {
            if (listener instanceof Button button
                    && button.getX() == screen.width - BUTTON_SIZE
                    && button.getY() >= screen.height / 2) {
                topmostRightButton = Math.min(topmostRightButton, button.getY());
            }
        }
        return Math.max(0, topmostRightButton - BUTTON_SIZE);
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() == activeMapScreen) {
            clearSearch();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (activeMapScreen == null) {
            return;
        }
        if (Minecraft.getInstance().screen != activeMapScreen) {
            clearSearch();
            return;
        }
        applyCompletedClusterBuild();
        processSnapshotJob();

        if (claimsDirty && claimChangeDebounce > 0) {
            claimChangeDebounce--;
        }
        if (claimsDirty
                && claimChangeDebounce <= 0
                && snapshotJob == null
                && !clusterBuildInFlight
                && selectedPlayerId != null
                && clusterDimension != null) {
            claimsDirty = false;
            beginClusterSnapshot(
                    selectedPlayerId,
                    clusterDimension,
                    false,
                    false
            );
        }
    }

    // Kept for compatibility with the older Xaero highlighter mixin. Claim search now navigates the map and never hides a claim.
    public static IPlayerChunkClaimAPI filterClaim(IPlayerChunkClaimAPI claim) {
        return claim;
    }

    public static boolean isSelectedClusterChunk(ResourceLocation dimension,
                                                 int chunkX,
                                                 int chunkZ) {
        SelectedClusterHighlight highlight = selectedHighlight;
        return Objects.equals(highlight.dimension(), dimension)
                && chunkX >= highlight.minX()
                && chunkX <= highlight.maxX()
                && chunkZ >= highlight.minZ()
                && chunkZ <= highlight.maxZ()
                && highlight.chunks().contains(new ClaimChunk(chunkX, chunkZ));
    }

    public static int getSelectedClusterHighlightRevision() {
        return selectedHighlight.revision();
    }

    private static void updateQuery(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (query.equals(normalized)) {
            return;
        }
        query = normalized;
        refreshSearch(true);
    }

    private static void refreshSearch(boolean centerFirst) {
        List<PlayerMatch> newMatches = query.isEmpty()
                ? List.of()
                : findMatchingPlayers(query);
        PlayerMatch selectedPlayer = selectPlayer(newMatches, query);
        UUID newSelectedPlayerId = selectedPlayer == null ? null : selectedPlayer.id();
        ResourceLocation newClusterDimension = selectedPlayer == null
                ? null
                : getCurrentMapDimension();

        matchingPlayers = newMatches;
        selectedPlayerId = newSelectedPlayerId;
        clusterDimension = newClusterDimension;
        claimsDirty = false;
        claimChangeDebounce = 0;

        if (selectedPlayer == null || newClusterDimension == null) {
            cancelPendingClusterWork();
            clusters = List.of();
            clusterIndex = -1;
            syncSelectedClusterHighlight();
            updateSearchBoxColor();
            updateNavigationButtons();
            return;
        }

        beginClusterSnapshot(
                selectedPlayer.id(),
                newClusterDimension,
                true,
                centerFirst
        );
    }

    private static List<PlayerMatch> findMatchingPlayers(String normalizedQuery) {
        try {
            return OpenPACClientAPI.get()
                    .getClaimsManager()
                    .getPlayerInfoStream()
                    .filter(info -> info.getPlayerUsername() != null)
                    .filter(info -> info.getPlayerUsername()
                            .toLowerCase(Locale.ROOT)
                            .contains(normalizedQuery))
                    .map(info -> new PlayerMatch(
                            info.getPlayerId(),
                            info.getPlayerUsername()
                    ))
                    .sorted(Comparator
                            .comparing(
                                    (PlayerMatch match) -> match.username()
                                            .toLowerCase(Locale.ROOT)
                            )
                            .thenComparing(PlayerMatch::id))
                    .toList();
        } catch (LinkageError | RuntimeException exception) {
            return List.of();
        }
    }

    private static PlayerMatch selectPlayer(List<PlayerMatch> matches,
                                            String normalizedQuery) {
        if (matches.isEmpty()) {
            return null;
        }
        return matches.stream()
                .filter(match -> match.username().equalsIgnoreCase(normalizedQuery))
                .findFirst()
                .orElse(matches.getFirst());
    }

    private static void ensureClaimListenerRegistered() {
        if (claimListenerRegistered) {
            return;
        }
        try {
            OpenPACClientAPI.get()
                    .getClaimsManager()
                    .getTracker()
                    .register(new ClaimChangeListener());
            claimListenerRegistered = true;
        } catch (LinkageError | RuntimeException exception) {
            if (REPORTED_SEARCH_FAILURE.compareAndSet(false, true)) {
                LOGGER.warn(
                        "[OPAC Essentials] Could not register the claim-search listener.",
                        exception
                );
            }
        }
    }

    private static void beginClusterSnapshot(UUID playerId,
                                             ResourceLocation dimension,
                                             boolean clearExisting,
                                             boolean centerWhenReady) {
        cancelPendingClusterWork();
        long generation = searchGeneration;

        if (clearExisting) {
            clusters = List.of();
            clusterIndex = -1;
            syncSelectedClusterHighlight();
            updateSearchBoxColor();
            updateNavigationButtons();
        }

        try {
            IClientClaimsManagerAPI manager = OpenPACClientAPI.get().getClaimsManager();
            if (!manager.hasPlayerInfo(playerId)) {
                applyClusterBuild(new ClusterBuildResult(
                        generation,
                        playerId,
                        dimension,
                        List.of(),
                        centerWhenReady
                ));
                return;
            }

            IClientPlayerClaimInfoAPI playerInfo = manager.getPlayerInfo(playerId);
            IPlayerDimensionClaimsAPI dimensionClaims = playerInfo.getDimension(dimension);
            List<IPlayerClaimPosListAPI> claimLists = dimensionClaims == null
                    ? List.of()
                    : dimensionClaims.getStream().toList();
            snapshotJob = new SnapshotJob(
                    generation,
                    playerId,
                    dimension,
                    claimLists.iterator(),
                    centerWhenReady
            );
        } catch (LinkageError | RuntimeException exception) {
            reportSearchFailure("start the claim snapshot", exception);
            applyClusterBuild(new ClusterBuildResult(
                    generation,
                    playerId,
                    dimension,
                    List.of(),
                    centerWhenReady
            ));
        }
    }

    private static void processSnapshotJob() {
        SnapshotJob job = snapshotJob;
        if (job == null || job.generation != searchGeneration) {
            return;
        }

        long deadline = System.nanoTime() + SNAPSHOT_TIME_BUDGET_NANOS;
        int processed = 0;
        try {
            while (processed < SNAPSHOT_CHUNKS_PER_TICK
                    && System.nanoTime() < deadline) {
                if (job.currentClaims == null || !job.currentClaims.hasNext()) {
                    if (!job.claimLists.hasNext()) {
                        finishSnapshotJob(job);
                        return;
                    }
                    job.currentClaims = job.claimLists.next().getStream().iterator();
                    continue;
                }

                job.claims.add(toClaimChunk(job.currentClaims.next()));
                processed++;
            }
        } catch (LinkageError | RuntimeException exception) {
            snapshotJob = null;
            reportSearchFailure("read the claim snapshot", exception);
            claimsDirty = true;
            claimChangeDebounce = CLAIM_CHANGE_DEBOUNCE_TICKS;
        }
    }

    private static void finishSnapshotJob(SnapshotJob job) {
        snapshotJob = null;
        clusterBuildInFlight = true;
        Set<ClaimChunk> immutableSnapshot = Set.copyOf(job.claims);

        CompletableFuture.supplyAsync(() -> new ClusterBuildResult(
                job.generation,
                job.playerId,
                job.dimension,
                buildClusters(immutableSnapshot),
                job.centerWhenReady
        )).whenComplete((result, error) -> {
            if (job.generation != searchGeneration) {
                return;
            }
            if (error != null) {
                reportSearchFailure("calculate claim clusters", error);
                completedClusterBuild = new ClusterBuildResult(
                        job.generation,
                        job.playerId,
                        job.dimension,
                        List.of(),
                        job.centerWhenReady
                );
            } else {
                completedClusterBuild = result;
            }
        });
    }

    private static void applyCompletedClusterBuild() {
        ClusterBuildResult result = completedClusterBuild;
        if (result == null) {
            return;
        }
        completedClusterBuild = null;
        if (result.generation != searchGeneration) {
            return;
        }
        clusterBuildInFlight = false;
        applyClusterBuild(result);
    }

    private static void applyClusterBuild(ClusterBuildResult result) {
        if (result.generation != searchGeneration
                || !Objects.equals(selectedPlayerId, result.playerId)
                || !Objects.equals(clusterDimension, result.dimension)) {
            return;
        }

        ClaimCluster previousCluster = clusterIndex >= 0 && clusterIndex < clusters.size()
                ? clusters.get(clusterIndex)
                : null;
        boolean clustersJustAppeared = clusters.isEmpty() && !result.clusters.isEmpty();
        clusters = result.clusters;

        if (clusters.isEmpty()) {
            clusterIndex = -1;
        } else if (result.centerWhenReady || previousCluster == null) {
            clusterIndex = 0;
        } else {
            int preservedIndex = clusters.indexOf(previousCluster);
            clusterIndex = preservedIndex >= 0
                    ? preservedIndex
                    : Math.min(clusterIndex, clusters.size() - 1);
        }

        updateSearchBoxColor();
        updateNavigationButtons();
        syncSelectedClusterHighlight();

        if (!clusters.isEmpty() && (result.centerWhenReady || clustersJustAppeared)) {
            centerCurrentCluster();
        }
    }

    private static void cancelPendingClusterWork() {
        searchGeneration++;
        snapshotJob = null;
        clusterBuildInFlight = false;
        completedClusterBuild = null;
    }

    private static void markClaimsDirty(ResourceLocation dimension) {
        if (selectedPlayerId == null || !Objects.equals(clusterDimension, dimension)) {
            return;
        }
        claimsDirty = true;
        claimChangeDebounce = CLAIM_CHANGE_DEBOUNCE_TICKS;
    }

    private static void reportSearchFailure(String action, Throwable exception) {
        if (REPORTED_SEARCH_FAILURE.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[OPAC Essentials] Could not " + action + ".",
                    exception
            );
        }
    }

    private static ClaimChunk toClaimChunk(ChunkPos position) {
        return new ClaimChunk(position.x, position.z);
    }

    private static List<ClaimCluster> buildClusters(Set<ClaimChunk> claims) {
        if (claims.isEmpty()) {
            return List.of();
        }

        Set<ClaimChunk> remaining = new HashSet<>(claims);
        List<ClaimCluster> result = new ArrayList<>();
        ArrayDeque<ClaimChunk> open = new ArrayDeque<>();

        while (!remaining.isEmpty()) {
            ClaimChunk first = remaining.iterator().next();
            remaining.remove(first);
            open.add(first);
            int minX = first.x();
            int maxX = first.x();
            int minZ = first.z();
            int maxZ = first.z();
            int count = 0;
            Set<ClaimChunk> clusterChunks = new HashSet<>();

            while (!open.isEmpty()) {
                ClaimChunk current = open.removeFirst();
                count++;
                clusterChunks.add(current);
                minX = Math.min(minX, current.x());
                maxX = Math.max(maxX, current.x());
                minZ = Math.min(minZ, current.z());
                maxZ = Math.max(maxZ, current.z());

                addIfUnvisited(remaining, open, current.x() - 1, current.z());
                addIfUnvisited(remaining, open, current.x() + 1, current.z());
                addIfUnvisited(remaining, open, current.x(), current.z() - 1);
                addIfUnvisited(remaining, open, current.x(), current.z() + 1);
            }

            result.add(new ClaimCluster(
                    minX,
                    minZ,
                    maxX,
                    maxZ,
                    count,
                    Set.copyOf(clusterChunks)
            ));
        }

        result.sort(CLUSTER_ORDER);
        return List.copyOf(result);
    }

    private static void addIfUnvisited(Set<ClaimChunk> remaining,
                                       ArrayDeque<ClaimChunk> open,
                                       int x,
                                       int z) {
        ClaimChunk neighbor = new ClaimChunk(x, z);
        if (remaining.remove(neighbor)) {
            open.addLast(neighbor);
        }
    }

    private static void cycleCluster(int direction) {
        if (clusters.size() <= 1) {
            return;
        }
        clusterIndex = Math.floorMod(clusterIndex + direction, clusters.size());
        syncSelectedClusterHighlight();
        centerCurrentCluster();
    }

    private static void centerCurrentCluster() {
        if (activeMapScreen == null
                || clusterIndex < 0
                || clusterIndex >= clusters.size()) {
            return;
        }

        ClaimCluster cluster = clusters.get(clusterIndex);
        try {
            try {
                Field attachedCamera = findField(
                        activeMapScreen.getClass(),
                        "attachedCamera"
                );
                attachedCamera.setAccessible(true);
                attachedCamera.setBoolean(null, false);
            } catch (NoSuchFieldException ignored) {
                // Older Xaero World Map versions don't have attached camera mode.
            }

            Field shouldResetCamera = findField(
                    activeMapScreen.getClass(),
                    "shouldResetCameraPos"
            );
            shouldResetCamera.setAccessible(true);
            shouldResetCamera.setBoolean(activeMapScreen, false);

            Field cameraDestination = findField(
                    activeMapScreen.getClass(),
                    "cameraDestination"
            );
            cameraDestination.setAccessible(true);
            cameraDestination.set(activeMapScreen, new int[]{
                    cluster.centerBlockX(),
                    cluster.centerBlockZ()
            });
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            reportXaeroFailure("center the map", exception);
        }
    }

    private static void syncSelectedClusterHighlight() {
        ClaimCluster cluster = clusterIndex >= 0 && clusterIndex < clusters.size()
                ? clusters.get(clusterIndex)
                : null;
        ResourceLocation dimension = cluster == null ? null : clusterDimension;
        Set<ClaimChunk> chunks = cluster == null ? Set.of() : cluster.chunks();
        SelectedClusterHighlight previous = selectedHighlight;
        if (Objects.equals(previous.dimension(), dimension)
                && previous.chunks().equals(chunks)) {
            return;
        }

        SelectedClusterHighlight next = new SelectedClusterHighlight(
                dimension,
                chunks,
                cluster == null ? 0 : cluster.minX(),
                cluster == null ? 0 : cluster.minZ(),
                cluster == null ? -1 : cluster.maxX(),
                cluster == null ? -1 : cluster.maxZ(),
                previous.revision() + 1
        );
        selectedHighlight = next;
        invalidateXaeroHighlightCache(previous, next);
    }

    private static ResourceLocation getCurrentMapDimension() {
        try {
            Class<?> sessionClass = Class.forName(
                    "xaero.map.WorldMapSession",
                    false,
                    ClaimSearchClient.class.getClassLoader()
            );
            Object session = invokeStatic(sessionClass, "getCurrentSession");
            Object mapProcessor = invoke(session, "getMapProcessor");
            Object mapWorld = invoke(mapProcessor, "getMapWorld");
            Object dimensionKey = invoke(mapWorld, "getCurrentDimensionId");
            Object location = invoke(dimensionKey, "location");
            if (location instanceof ResourceLocation resourceLocation) {
                return resourceLocation;
            }
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            reportXaeroFailure("read the current map dimension", exception);
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null
                ? null
                : minecraft.level.dimension().location();
    }

    private static void invalidateXaeroHighlightCache(
            SelectedClusterHighlight... highlights) {
        Map<ResourceLocation, Set<Long>> affectedRegions = new HashMap<>();
        for (SelectedClusterHighlight highlight : highlights) {
            if (highlight.dimension() == null || highlight.chunks().isEmpty()) {
                continue;
            }
            Set<Long> regions = affectedRegions.computeIfAbsent(
                    highlight.dimension(),
                    ignored -> new HashSet<>()
            );
            for (ClaimChunk chunk : highlight.chunks()) {
                int regionX = chunk.x() >> 5;
                int regionZ = chunk.z() >> 5;
                regions.add((long) regionZ << 32 | (long) regionX & 0xFFFFFFFFL);
            }
        }
        if (affectedRegions.isEmpty()) {
            return;
        }

        try {
            Class<?> sessionClass = Class.forName(
                    "xaero.map.WorldMapSession",
                    false,
                    ClaimSearchClient.class.getClassLoader()
            );
            Object session = invokeStatic(sessionClass, "getCurrentSession");
            Object mapProcessor = invoke(session, "getMapProcessor");
            Object mapWorld = invoke(mapProcessor, "getMapWorld");
            Object dimensions = invoke(mapWorld, "getDimensionsList");
            if (!(dimensions instanceof Iterable<?> iterable)) {
                return;
            }
            for (Object dimension : iterable) {
                Object dimensionKey = invoke(dimension, "getDimId");
                Object location = invoke(dimensionKey, "location");
                if (!(location instanceof ResourceLocation resourceLocation)) {
                    continue;
                }
                Set<Long> regions = affectedRegions.get(resourceLocation);
                if (regions == null) {
                    continue;
                }
                Object highlightHandler = invoke(dimension, "getHighlightHandler");
                Method clearCachedHash = highlightHandler.getClass().getMethod(
                        "clearCachedHash",
                        int.class,
                        int.class
                );
                for (long region : regions) {
                    invokeMethod(
                            clearCachedHash,
                            highlightHandler,
                            (int) region,
                            (int) (region >> 32)
                    );
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Xaero's World Map is optional.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            reportXaeroFailure("refresh claim highlights", exception);
        }
    }

    private static Field findField(Class<?> owner, String fieldName)
            throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static void reportXaeroFailure(String action, Throwable exception) {
        if (REPORTED_XAERO_FAILURE.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[OPAC Essentials] Could not " + action
                            + " in Xaero's World Map.",
                    exception
            );
        }
    }

    private static Object invoke(Object target, String methodName)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        return invokeMethod(method, target);
    }

    private static Object invokeStatic(Class<?> owner, String methodName)
            throws ReflectiveOperationException {
        Method method = owner.getMethod(methodName);
        return invokeMethod(method, null);
    }

    private static Object invokeMethod(Method method, Object target, Object... arguments)
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

    private static void updateSearchBoxColor() {
        if (searchBox == null) {
            return;
        }

        int color;
        if (query.isEmpty()) {
            color = COLOR_NORMAL;
        } else if (matchingPlayers.isEmpty()) {
            color = COLOR_NO_MATCH;
        } else if (clusters.isEmpty() || matchingPlayers.size() > 1) {
            color = COLOR_MULTIPLE_MATCHES;
        } else {
            color = COLOR_ONE_MATCH;
        }
        searchBox.setTextColor(color);
    }

    private static void updateNavigationButtons() {
        boolean multipleClusters = clusters.size() > 1;
        if (previousButton != null) {
            previousButton.active = multipleClusters;
        }
        if (nextButton != null) {
            nextButton.active = multipleClusters;
        }
    }

    private static void toggleSearch(Screen screen) {
        if (screen != activeMapScreen) {
            return;
        }

        searchOpen = !searchOpen;
        if (!searchOpen) {
            resetSearch();
        }
        screen.init(Minecraft.getInstance(), screen.width, screen.height);
    }

    private static void resetSearch() {
        cancelPendingClusterWork();
        searchBox = null;
        previousButton = null;
        nextButton = null;
        query = "";
        matchingPlayers = List.of();
        clusters = List.of();
        clusterDimension = null;
        selectedPlayerId = null;
        clusterIndex = -1;
        claimsDirty = false;
        claimChangeDebounce = 0;
        syncSelectedClusterHighlight();
    }

    private static void clearSearch() {
        activeMapScreen = null;
        searchOpen = false;
        resetSearch();
    }

    private static boolean isXaeroMapScreen(Screen screen) {
        return screen != null
                && XAERO_MAP_SCREEN.equals(screen.getClass().getName());
    }

    private record PlayerMatch(UUID id, String username) {
    }

    private record ClaimChunk(int x, int z) {
    }

    private record ClaimCluster(int minX, int minZ, int maxX, int maxZ, int count,
                                Set<ClaimChunk> chunks) {
        private int centerBlockX() {
            return (int) (((long) minX + maxX + 1L) * 8L);
        }

        private int centerBlockZ() {
            return (int) (((long) minZ + maxZ + 1L) * 8L);
        }
    }

    private record SelectedClusterHighlight(ResourceLocation dimension,
                                            Set<ClaimChunk> chunks,
                                            int minX,
                                            int minZ,
                                            int maxX,
                                            int maxZ,
                                            int revision) {
    }

    private record ClusterBuildResult(long generation,
                                      UUID playerId,
                                      ResourceLocation dimension,
                                      List<ClaimCluster> clusters,
                                      boolean centerWhenReady) {
    }

    private static final class SnapshotJob {
        private final long generation;
        private final UUID playerId;
        private final ResourceLocation dimension;
        private final Iterator<IPlayerClaimPosListAPI> claimLists;
        private final boolean centerWhenReady;
        private final Set<ClaimChunk> claims = new HashSet<>();
        private Iterator<ChunkPos> currentClaims;

        private SnapshotJob(long generation,
                            UUID playerId,
                            ResourceLocation dimension,
                            Iterator<IPlayerClaimPosListAPI> claimLists,
                            boolean centerWhenReady) {
            this.generation = generation;
            this.playerId = playerId;
            this.dimension = dimension;
            this.claimLists = claimLists;
            this.centerWhenReady = centerWhenReady;
        }
    }

    private static final class ClaimChangeListener implements IClaimsManagerListenerAPI {
        @Override
        public void onWholeRegionChange(ResourceLocation dimension,
                                        int regionX,
                                        int regionZ) {
            markClaimsDirty(dimension);
        }

        @Override
        public void onChunkChange(ResourceLocation dimension,
                                  int chunkX,
                                  int chunkZ,
                                  IPlayerChunkClaimAPI claim) {
            markClaimsDirty(dimension);
        }

        @Override
        public void onDimensionChange(ResourceLocation dimension) {
            markClaimsDirty(dimension);
        }
    }

    private static final class SearchToggleButton extends Button {
        private final boolean open;

        private SearchToggleButton(int x, int y, boolean open, OnPress onPress) {
            super(
                    x,
                    y,
                    BUTTON_SIZE,
                    BUTTON_SIZE,
                    Component.literal("Claim Search"),
                    onPress,
                    DEFAULT_NARRATION
            );
            this.open = open;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int iconX = getX() + 6;
            int iconY = getY() + 6 - (isHoveredOrFocused() ? 1 : 0);
            int color = !active ? 0xFF555555 : open ? 0xFF55FF55 : 0xFFFDFDFD;

            drawFocus(graphics);
            drawMagnifier(graphics, iconX + 1, iconY + 1, 0xA0000000);
            drawMagnifier(graphics, iconX, iconY, color);
        }

        private void drawMagnifier(GuiGraphics graphics, int x, int y, int color) {
            graphics.fill(x + 1, y, x + 4, y + 1, color);
            graphics.fill(x, y + 1, x + 1, y + 4, color);
            graphics.fill(x + 4, y + 1, x + 5, y + 4, color);
            graphics.fill(x + 1, y + 4, x + 4, y + 5, color);
            graphics.fill(x + 4, y + 4, x + 6, y + 6, color);
            graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
        }

        private void drawFocus(GuiGraphics graphics) {
            if (isFocused()) {
                graphics.fill(
                        getX() + 4,
                        getY() + 3,
                        getX() + 16,
                        getY() + 17,
                        0x55FFFFFF
                );
            }
        }
    }

    private static final class ClusterNavButton extends Button {
        private final int direction;

        private ClusterNavButton(int x, int y, int direction, OnPress onPress) {
            super(
                    x,
                    y,
                    BUTTON_SIZE,
                    BUTTON_SIZE,
                    Component.literal(direction < 0
                            ? "Previous claim cluster"
                            : "Next claim cluster"),
                    onPress,
                    DEFAULT_NARRATION
            );
            this.direction = direction;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int centerX = getX() + 10;
            int centerY = getY() + 10 - (isHoveredOrFocused() ? 1 : 0);
            int color = active ? 0xFFFDFDFD : 0xFF555555;

            drawArrow(graphics, centerX + 1, centerY + 1, 0xA0000000);
            drawArrow(graphics, centerX, centerY, color);
        }

        private void drawArrow(GuiGraphics graphics, int centerX, int centerY,
                               int color) {
            for (int row = -3; row <= 3; row++) {
                int innerEdge = -3 + Math.abs(row) * 2;
                if (direction < 0) {
                    graphics.fill(
                            centerX + innerEdge,
                            centerY + row,
                            centerX + 4,
                            centerY + row + 1,
                            color
                    );
                } else {
                    graphics.fill(
                            centerX - 3,
                            centerY + row,
                            centerX - innerEdge + 1,
                            centerY + row + 1,
                            color
                    );
                }
            }
        }
    }
}
