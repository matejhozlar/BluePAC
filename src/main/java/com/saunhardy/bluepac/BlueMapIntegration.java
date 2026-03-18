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

        claimsManager.getPlayerInfoStream().forEach(playerInfo -> {
            var playerId = playerInfo.getPlayerId();
            var playerName = playerInfo.getPlayerUsername();

            playerInfo.getStream().forEach(entry -> {
                ResourceLocation dimension = entry.getKey();
                String dimKey = dimension.toString();
                var dimClaims = entry.getValue();

                dimClaims.getStream().forEach(claimPosList -> {
                    var claimState = claimPosList.getClaimState();
                    int subConfigIndex = claimState.getSubConfigIndex();
                    Integer subColor = playerInfo.getClaimsColor(subConfigIndex);
                    int color = subColor != null ? subColor : playerInfo.getClaimsColor();
                    String subName = playerInfo.getClaimsName(subConfigIndex);
                    String claimName = subName != null ? subName : playerInfo.getClaimsName();

                    String groupKey = playerId + "_" + subConfigIndex;
                    String label = buildLabel(playerName, playerId.toString(), claimName);

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
     * Collects border edges (edges between a chunk in the set and an empty neighbor),
     * then follows the chain to form a closed polygon. For shapes with holes, returns
     * only the exterior (largest area) loop.
     */
    private static List<int[]> traceExteriorOutline(Set<ChunkPos> chunks) {
        // Build adjacency graph from border edges
        Map<Long, List<Long>> adjacency = new HashMap<>();

        for (ChunkPos chunk : chunks) {
            int x0 = chunk.x * 16, z0 = chunk.z * 16;
            int x1 = (chunk.x + 1) * 16, z1 = (chunk.z + 1) * 16;

            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1)))
                addEdge(adjacency, packPoint(x0, z0), packPoint(x1, z0));
            if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z)))
                addEdge(adjacency, packPoint(x1, z0), packPoint(x1, z1));
            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1)))
                addEdge(adjacency, packPoint(x1, z1), packPoint(x0, z1));
            if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z)))
                addEdge(adjacency, packPoint(x0, z1), packPoint(x0, z0));
        }

        // Trace all loops
        Set<Long> visited = new HashSet<>();
        List<List<int[]>> loops = new ArrayList<>();

        for (long start : adjacency.keySet()) {
            if (visited.contains(start)) continue;

            List<int[]> loop = new ArrayList<>();
            long current = start;
            long prev = -1;

            do {
                visited.add(current);
                loop.add(new int[]{unpackX(current), unpackZ(current)});
                List<Long> neighbors = adjacency.getOrDefault(current, List.of());
                long next = -1;
                for (long n : neighbors) {
                    if (n != prev) {
                        next = n;
                        break;
                    }
                }
                prev = current;
                current = next;
            } while (current != -1 && current != start);

            loops.add(loop);
        }

        // Return the loop with the largest area (the exterior outline)
        List<int[]> exterior = null;
        double maxArea = 0;
        for (List<int[]> loop : loops) {
            double area = Math.abs(signedArea(loop));
            if (area > maxArea) {
                maxArea = area;
                exterior = loop;
            }
        }

        return exterior != null ? exterior : List.of();
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

    private static double signedArea(List<int[]> points) {
        double area = 0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            int[] curr = points.get(i);
            int[] next = points.get((i + 1) % n);
            area += (double) curr[0] * next[1] - (double) next[0] * curr[1];
        }
        return area / 2.0;
    }

    private static void addEdge(Map<Long, List<Long>> adj, long a, long b) {
        adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        adj.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
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

    private static String buildLabel(String playerName, String playerIdStr, String claimName) {
        String label = (playerName != null && !playerName.isEmpty()) ? playerName : playerIdStr;
        if (claimName != null && !claimName.isEmpty()) {
            label += " - " + claimName;
        }
        return label;
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
