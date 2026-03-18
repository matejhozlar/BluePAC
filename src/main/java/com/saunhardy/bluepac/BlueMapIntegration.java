package com.saunhardy.bluepac;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.api.PlayerConfigType;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class BlueMapIntegration {
    private static final String MARKER_SET_ID = "bluepac-claims";

    private static Consumer<BlueMapAPI> onEnableListener;
    private static Consumer<BlueMapAPI> onDisableListener;
    private static MinecraftServer server;
    private static BlueMapAPI blueMapApi;
    private static final Map<String, MarkerSet> markerSets = new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> pendingReload;

    public static void init(MinecraftServer mcServer) {
        server = mcServer;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BluePAC-Scheduler");
            t.setDaemon(true);
            return t;
        });

        onEnableListener = api -> {
            blueMapApi = api;
            BluePAC.LOGGER.info("BlueMap API enabled, loading claim markers...");
            try {
                loadAllClaims(api);
                registerClaimListener();
                BluePAC.LOGGER.info("BluePAC loaded markers for {} dimension(s).", markerSets.size());
            } catch (Exception e) {
                BluePAC.LOGGER.error("Failed to load claim markers", e);
            }
        };

        onDisableListener = api -> {
            blueMapApi = null;
            markerSets.clear();
            BluePAC.LOGGER.info("BlueMap API disabled, markers cleared.");
        };

        BlueMapAPI.onEnable(onEnableListener);
        BlueMapAPI.onDisable(onDisableListener);
        BlueMapAPI.getInstance().ifPresent(onEnableListener);
    }

    public static void shutdown() {
        if (onEnableListener != null) {
            BlueMapAPI.unregisterListener(onEnableListener);
            onEnableListener = null;
        }
        if (onDisableListener != null) {
            BlueMapAPI.unregisterListener(onDisableListener);
            onDisableListener = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        pendingReload = null;
        blueMapApi = null;
        markerSets.clear();
        server = null;
    }

    // ── Claim loading ───────────────────────────────────────────────────

    private static synchronized void loadAllClaims(BlueMapAPI api) {
        for (BlueMapMap map : api.getMaps()) {
            map.getMarkerSets().remove(MARKER_SET_ID);
        }
        markerSets.clear();

        var claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();

        float fillOpacity = Config.FILL_OPACITY.get().floatValue();
        float lineOpacity = Config.LINE_OPACITY.get().floatValue();
        int lineWidth = Config.LINE_WIDTH.get();
        int markerY = Config.MARKER_Y_HEIGHT.get();

        // Group chunks by dimension → (player+subConfig) → set of ChunkPos
        Map<String, Map<String, ClaimGroup>> dimensionGroups = new HashMap<>();

        var playerConfigs = OpenPACServerAPI.get(server).getPlayerConfigs();

        claimsManager.getPlayerInfoStream().forEach(playerInfo -> {
            var playerId = playerInfo.getPlayerId();
            var playerName = resolvePlayerName(playerInfo.getPlayerUsername(), playerId);

            // Check if this player's claims are expired
            var config = playerConfigs.getLoadedConfig(playerId);
            boolean expired = config != null && config.getType() == PlayerConfigType.EXPIRED;

            playerInfo.getStream().forEach(entry -> {
                ResourceLocation dimension = entry.getKey();
                String dimKey = dimension.toString();
                var dimClaims = entry.getValue();

                dimClaims.getStream().forEach(claimPosList -> {
                    var claimState = claimPosList.getClaimState();
                    int subConfigIndex = claimState.getSubConfigIndex();
                    int color = playerInfo.getClaimsColor();
                    String subName = playerInfo.getClaimsName(subConfigIndex);
                    String claimName = subName != null ? subName : playerInfo.getClaimsName();

                    String groupKey = playerId + "_" + subConfigIndex;
                    String label = buildLabel(playerName, playerId.toString(), claimName, expired);

                    ClaimGroup group = dimensionGroups
                            .computeIfAbsent(dimKey, k -> new HashMap<>())
                            .computeIfAbsent(groupKey, k -> new ClaimGroup(label, color, new HashSet<>()));

                    claimPosList.getStream().forEach(chunkPos -> group.chunks.add(chunkPos));
                });
            });
        });

        // Merge chunks into polygons and create markers
        for (var dimEntry : dimensionGroups.entrySet()) {
            String dimKey = dimEntry.getKey();
            MarkerSet markerSet = MarkerSet.builder()
                    .label("Claimed Chunks")
                    .defaultHidden(false)
                    .build();

            int markerIndex = 0;

            for (var groupEntry : dimEntry.getValue().entrySet()) {
                ClaimGroup group = groupEntry.getValue();
                int r = (group.color >> 16) & 0xFF;
                int g = (group.color >> 8) & 0xFF;
                int b = group.color & 0xFF;
                Color lineColor = new Color(r, g, b, lineOpacity);
                Color fillColor = new Color(r, g, b, fillOpacity);

                List<Set<ChunkPos>> components = findConnectedComponents(group.chunks);

                for (Set<ChunkPos> component : components) {
                    List<int[]> outline = traceExteriorOutline(component);
                    if (outline.size() < 3) continue;

                    outline = removeCollinearPoints(outline);
                    if (outline.size() < 3) continue;

                    Shape shape = createShapeFromPoints(outline);

                    ShapeMarker marker = ShapeMarker.builder()
                            .label(group.label)
                            .shape(shape, markerY)
                            .lineColor(lineColor)
                            .fillColor(fillColor)
                            .lineWidth(lineWidth)
                            .depthTestEnabled(false)
                            .build();

                    markerSet.getMarkers().put("claim_" + markerIndex++, marker);
                }
            }

            markerSets.put(dimKey, markerSet);
        }

        // Attach marker sets to matching BlueMap maps
        for (BlueMapMap map : api.getMaps()) {
            String dimKey = extractDimensionKey(map.getWorld().getId());
            MarkerSet markerSet = markerSets.get(dimKey);
            if (markerSet != null) {
                map.getMarkerSets().put(MARKER_SET_ID, markerSet);
            }
        }
    }

    // ── Change listener with debounce ───────────────────────────────────

    private static void registerClaimListener() {
        var claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();

        claimsManager.getTracker().register(new IClaimsManagerListenerAPI() {
            @Override
            public void onChunkChange(ResourceLocation dimension, int chunkX, int chunkZ, IPlayerChunkClaimAPI claim) {
                scheduleReload();
            }

            @Override
            public void onWholeRegionChange(ResourceLocation dimension, int regionX, int regionZ) {
                scheduleReload();
            }

            @Override
            public void onDimensionChange(ResourceLocation dimension) {
                scheduleReload();
            }
        });
    }

    private static void scheduleReload() {
        if (blueMapApi == null || scheduler == null || scheduler.isShutdown()) return;
        synchronized (BlueMapIntegration.class) {
            if (pendingReload != null) pendingReload.cancel(false);
            pendingReload = scheduler.schedule(() -> {
                if (blueMapApi != null && server != null) {
                    server.execute(() -> loadAllClaims(blueMapApi));
                }
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    // ── Polygon merging algorithm ───────────────────────────────────────

    /**
     * Finds connected components in a set of chunks using 4-connectivity flood fill.
     */
    private static List<Set<ChunkPos>> findConnectedComponents(Set<ChunkPos> chunks) {
        Set<ChunkPos> remaining = new HashSet<>(chunks);
        List<Set<ChunkPos>> components = new ArrayList<>();

        while (!remaining.isEmpty()) {
            ChunkPos start = remaining.iterator().next();
            Set<ChunkPos> component = new HashSet<>();
            Deque<ChunkPos> queue = new ArrayDeque<>();
            queue.add(start);
            remaining.remove(start);

            while (!queue.isEmpty()) {
                ChunkPos current = queue.poll();
                component.add(current);
                for (ChunkPos neighbor : neighbors(current)) {
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    /**
     * Traces the exterior outline polygon of a connected component of chunks.
     * Uses a right-hand (clockwise) wall-following rule to correctly handle
     * vertices where more than 2 edges meet.
     */
    private static List<int[]> traceExteriorOutline(Set<ChunkPos> chunks) {
        // Build adjacency graph from border edges (using Set to avoid duplicates)
        Map<Long, Set<Long>> adjacency = new HashMap<>();

        for (ChunkPos chunk : chunks) {
            int x0 = chunk.x * 16, z0 = chunk.z * 16;
            int x1 = (chunk.x + 1) * 16, z1 = (chunk.z + 1) * 16;

            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1)))
                addEdgeToSet(adjacency, packPoint(x0, z0), packPoint(x1, z0));
            if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z)))
                addEdgeToSet(adjacency, packPoint(x1, z0), packPoint(x1, z1));
            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1)))
                addEdgeToSet(adjacency, packPoint(x1, z1), packPoint(x0, z1));
            if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z)))
                addEdgeToSet(adjacency, packPoint(x0, z1), packPoint(x0, z0));
        }

        if (adjacency.isEmpty()) return List.of();

        // Find topmost-leftmost vertex (min z, then min x) — guaranteed on exterior
        long start = -1;
        int minZ = Integer.MAX_VALUE, minX = Integer.MAX_VALUE;
        for (long v : adjacency.keySet()) {
            int vz = unpackZ(v), vx = unpackX(v);
            if (vz < minZ || (vz == minZ && vx < minX)) {
                minZ = vz;
                minX = vx;
                start = v;
            }
        }

        if (start == -1) return List.of();

        // Trace exterior CW using right-hand rule.
        // Pretend we arrived going "up" (0,-1) so the first right-turn tries "right" (+x).
        List<int[]> outline = new ArrayList<>();
        long current = start;
        int dirX = 0, dirZ = -1;
        int safetyLimit = chunks.size() * 4 + 4;

        do {
            outline.add(new int[]{unpackX(current), unpackZ(current)});
            if (outline.size() > safetyLimit) {
                BluePAC.LOGGER.warn("Outline tracing exceeded safety limit ({} chunks), stopping.", chunks.size());
                break;
            }

            Set<Long> neighbors = adjacency.getOrDefault(current, Set.of());

            // Try CW directions: right-turn, straight, left-turn, u-turn
            int[][] turns = {
                    {-dirZ, dirX},    // right turn
                    {dirX, dirZ},     // straight
                    {dirZ, -dirX},    // left turn
                    {-dirX, -dirZ}    // u-turn
            };

            long next = -1;
            int nextDirX = 0, nextDirZ = 0;

            for (int[] turn : turns) {
                long candidate = packPoint(
                        unpackX(current) + turn[0] * 16,
                        unpackZ(current) + turn[1] * 16
                );
                if (neighbors.contains(candidate)) {
                    next = candidate;
                    nextDirX = turn[0];
                    nextDirZ = turn[1];
                    break;
                }
            }

            if (next == -1) break;

            dirX = nextDirX;
            dirZ = nextDirZ;
            current = next;
        } while (current != start);

        return outline;
    }

    private static void addEdgeToSet(Map<Long, Set<Long>> adj, long a, long b) {
        adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    /**
     * Removes collinear points from a polygon, keeping only corner vertices.
     * This turns e.g. 8 points for a single chunk rectangle into just 4.
     */
    private static List<int[]> removeCollinearPoints(List<int[]> points) {
        if (points.size() <= 3) return points;

        List<int[]> result = new ArrayList<>();
        int n = points.size();

        for (int i = 0; i < n; i++) {
            int[] prev = points.get((i - 1 + n) % n);
            int[] curr = points.get(i);
            int[] next = points.get((i + 1) % n);

            // Cross product of (curr-prev) and (next-curr); non-zero means a corner
            long cross = (long)(curr[0] - prev[0]) * (next[1] - curr[1])
                       - (long)(curr[1] - prev[1]) * (next[0] - curr[0]);

            if (cross != 0) {
                result.add(curr);
            }
        }

        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static Shape createShapeFromPoints(List<int[]> points) {
        Shape.Builder builder = Shape.builder();
        for (int[] point : points) {
            builder.addPoint(new com.flowpowered.math.vector.Vector2d(point[0], point[1]));
        }
        return builder.build();
    }

    private static long packPoint(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    private static List<ChunkPos> neighbors(ChunkPos pos) {
        return List.of(
                new ChunkPos(pos.x - 1, pos.z),
                new ChunkPos(pos.x + 1, pos.z),
                new ChunkPos(pos.x, pos.z - 1),
                new ChunkPos(pos.x, pos.z + 1)
        );
    }

    private static String extractDimensionKey(String blueMapWorldId) {
        int hashIndex = blueMapWorldId.indexOf('#');
        return (hashIndex >= 0) ? blueMapWorldId.substring(hashIndex + 1) : blueMapWorldId;
    }

    private static String buildLabel(String playerName, String playerIdStr, String claimName, boolean expired) {
        String label = (playerName != null && !playerName.isEmpty()) ? playerName : playerIdStr;
        if (claimName != null && !claimName.isEmpty()) {
            label += " - " + claimName;
        }
        if (expired) {
            label = "EXPIRED - " + label;
        }
        return label;
    }

    /**
     * Resolves a player name, falling back to the server's profile cache if the
     * name from OpenPAC is null (e.g. for expired/offline players).
     */
    private static String resolvePlayerName(String opacName, java.util.UUID playerId) {
        if (opacName != null && !opacName.isEmpty()) return opacName;
        if (server == null) return null;
        var cache = server.getProfileCache();
        if (cache == null) return null;
        return cache.get(playerId)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(null);
    }

    private static class ClaimGroup {
        final String label;
        final int color;
        final Set<ChunkPos> chunks;

        ClaimGroup(String label, int color, Set<ChunkPos> chunks) {
            this.label = label;
            this.color = color;
            this.chunks = chunks;
        }
    }
}
