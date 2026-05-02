package net.vibzz.woodlightingstandards.portal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.vibzz.woodlightingstandards.Woodlightingstandards;
import net.vibzz.woodlightingstandards.mixin.AreaHelperAccessor;
import net.vibzz.woodlightingstandards.util.PortalLightProbability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class WoodlightTracker {
    private static final WoodlightTracker INSTANCE = new WoodlightTracker();
    private final Map<RegistryKey<World>, CopyOnWriteArrayList<PortalLightEntry>> activeTimers = new ConcurrentHashMap<>();
    private final Map<RegistryKey<World>, Map<Long, PortalGroup>> activeGroups = new ConcurrentHashMap<>();
    private final Set<RegistryKey<World>> loadedWorlds = ConcurrentHashMap.newKeySet();

    private static final int SCAN_RADIUS = 12;
    private static final int SCAN_INTERVAL = 10;


    public static WoodlightTracker getInstance() {
        return INSTANCE;
    }

    public List<PortalLightEntry> getAllEntries(ServerWorld world) {
        return activeTimers.get(world.getRegistryKey());
    }

    public boolean isPortalSubChunk(ServerWorld world, int sectionX, int sectionY, int sectionZ) {
        List<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries == null || entries.isEmpty()) return false;

        long key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ);
        for (PortalLightEntry entry : entries) {
            if (entry.portalSubChunks.contains(key)) return true;
        }
        return false;
    }

    public boolean isPortalSubChunk(ServerWorld world, BlockPos pos) {
        return isPortalSubChunk(world, pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public Map<Long, PortalGroup> getActiveGroups(ServerWorld world) {
        return activeGroups.get(world.getRegistryKey());
    }

    public boolean isEnabled(ServerWorld world) {
        return world.getGameRules().getBoolean(Woodlightingstandards.STANDARDIZE_WOODLIGHT);
    }

    public void tick(ServerWorld world) {
        if (!isEnabled(world)) return;

        if (loadedWorlds.add(world.getRegistryKey())) {
            WoodlightPersistentState state = WoodlightPersistentState.get(world);
            List<PortalLightEntry> saved = state.getSavedEntries();
            if (!saved.isEmpty()) {
                activeTimers.computeIfAbsent(world.getRegistryKey(), k -> new CopyOnWriteArrayList<>()).addAll(saved);
            }
        }

        CopyOnWriteArrayList<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries != null && !entries.isEmpty()) {
            long currentTick = world.getTime();
            int currentDifficulty = world.getDifficulty().getId();
            List<PortalLightEntry> toRemove = new ArrayList<>();
            List<PortalLightEntry> activeEntries = new ArrayList<>();

            for (PortalLightEntry entry : entries) {
                if (!world.getChunkManager().isChunkLoaded(entry.probePos.getX() >> 4, entry.probePos.getZ() >> 4)) {
                    continue;
                }
                if (!isNearAnyPlayer(world, entry.probePos)) {
                    continue;
                }

                if (entry.lit) {
                    if (!world.getBlockState(entry.probePos).isOf(Blocks.NETHER_PORTAL)) {
                        toRemove.add(entry);
                    }
                    continue;
                }

                NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, entry.probePos);
                if (helper == null || !helper.isValid()) {
                    if (isInteriorAllPortalBlocks(world, entry)) {
                        entry.lit = true;
                        continue;
                    }
                    if (!isObsidianFrameIntact(world, entry)) {
                        toRemove.add(entry);
                    }
                    continue;
                }

                List<BlockPos> interiorBlocks = getInteriorBlocks(helper);

                if (isPortalLit(world, interiorBlocks)) {
                    entry.lit = true;
                    entry.cachedInterior = interiorBlocks;
                    entry.cachedFrame = getFrameBlocks(helper);
                    continue;
                }

                if (!entry.pendingExtinguish.isEmpty()) {
                    Iterator<Map.Entry<BlockPos, Long>> exIt = entry.pendingExtinguish.entrySet().iterator();
                    while (exIt.hasNext()) {
                        Map.Entry<BlockPos, Long> ex = exIt.next();
                        if (currentTick >= ex.getValue()) {
                            BlockPos pos = ex.getKey();
                            if (world.getBlockState(pos).isIn(BlockTags.FIRE)) {
                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                            }
                            exIt.remove();
                        }
                    }
                }

                List<BlockPos> flammable = getFlammableNeighbors(world, interiorBlocks);
                List<BlockPos> lava = getReachableLava(world, interiorBlocks);
                List<BlockPos> firePositions = findFireNear(world, interiorBlocks);
                List<BlockPos> zoneFlammable = getZoneFlammable(world, interiorBlocks);

                entry.cachedInterior = interiorBlocks;
                entry.cachedFrame = getFrameBlocks(helper);
                entry.cachedFlammable = flammable;
                entry.cachedZoneFlammable = zoneFlammable;
                entry.cachedLava = lava;
                entry.cachedFirePositions = firePositions;
                entry.cachedFireCount = firePositions.size();
                entry.updateSubChunks(interiorBlocks, flammable, lava);
                entry.perTickProbability = PortalLightProbability.compute(
                        world, interiorBlocks, currentDifficulty, entry.pendingExtinguish.keySet());

                activeEntries.add(entry);
            }

            List<List<PortalLightEntry>> components = computeGroups(activeEntries);

            Map<Long, PortalGroup> groupMap = activeGroups.computeIfAbsent(
                    world.getRegistryKey(), k -> new HashMap<>());
            Set<Long> seenKeys = new HashSet<>();

            for (List<PortalLightEntry> componentMembers : components) {
                long memberKey = PortalGroup.computeMemberKey(canonicalSortMembers(componentMembers));
                seenKeys.add(memberKey);

                PortalGroup group = groupMap.get(memberKey);
                if (group == null) {
                    int groupAttempt = WoodlightPersistentState.get(world).peekNextAttempt();
                    group = new PortalGroup(componentMembers, world.getSeed(), groupAttempt);
                    groupMap.put(memberKey, group);
                }

                for (PortalLightEntry member : group.members) {
                    member.accumulate(member.perTickProbability);
                }

                List<BlockPos> combinedInterior = group.getCombinedInteriorList();
                Set<BlockPos> excludePending;
                if (group.members.size() == 1) {
                    excludePending = group.members.get(0).pendingExtinguish.keySet();
                } else {
                    excludePending = new HashSet<>();
                    for (PortalLightEntry m : group.members) {
                        excludePending.addAll(m.pendingExtinguish.keySet());
                    }
                }
                group.scheduler.tick(world, combinedInterior, currentTick, currentDifficulty, excludePending);

                if (group.getCumulativeProbability() >= group.targetCumulative) {
                    PortalLightEntry winner = group.pickWinner();
                    NetherPortalBlock.AreaHelper winnerHelper =
                            NetherPortalBlock.createAreaHelper(world, winner.probePos);
                    if (winnerHelper != null && winnerHelper.isValid()) {
                        winnerHelper.createPortal();
                    }
                    winner.lit = true;
                    group.resetMemberContributions();
                    WoodlightPersistentState.get(world).commitAttempt();
                    groupMap.remove(memberKey);
                    seenKeys.remove(memberKey);
                }
            }

            groupMap.entrySet().removeIf(e -> !seenKeys.contains(e.getKey()));

            if (!toRemove.isEmpty()) {
                entries.removeAll(toRemove);
                persistEntries(world);
            }
        }

        if (world.getTime() % SCAN_INTERVAL == 0) {
            scanForSetups(world);
        }
    }

    private List<List<PortalLightEntry>> computeGroups(List<PortalLightEntry> activeEntries) {
        int n = activeEntries.size();
        if (n == 0) return new ArrayList<>();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        Map<BlockPos, Integer> ownerByPos = new HashMap<>();
        for (int i = 0; i < n; i++) {
            PortalLightEntry e = activeEntries.get(i);
            registerSet(parent, ownerByPos, e.cachedZoneFlammable, i);
            registerSet(parent, ownerByPos, e.cachedLava, i);
            registerSet(parent, ownerByPos, e.cachedFirePositions, i);
        }

        Map<Integer, List<PortalLightEntry>> components = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = ufFind(parent, i);
            components.computeIfAbsent(root, k -> new ArrayList<>()).add(activeEntries.get(i));
        }
        return new ArrayList<>(components.values());
    }

    private static void registerSet(int[] parent, Map<BlockPos, Integer> ownerByPos,
                                    List<BlockPos> positions, int idx) {
        if (positions == null) return;
        for (BlockPos pos : positions) {
            Integer existing = ownerByPos.putIfAbsent(pos, idx);
            if (existing != null && existing != idx) ufUnion(parent, existing, idx);
        }
    }

    private static int ufFind(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void ufUnion(int[] parent, int a, int b) {
        int ra = ufFind(parent, a);
        int rb = ufFind(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    private static List<PortalLightEntry> canonicalSortMembers(List<PortalLightEntry> members) {
        List<PortalLightEntry> sorted = new ArrayList<>(members);
        sorted.sort(Comparator
                .comparingLong((PortalLightEntry e) -> e.lowerCorner.asLong())
                .thenComparingInt(e -> e.axis.ordinal()));
        return sorted;
    }

    private void persistEntries(ServerWorld world) {
        CopyOnWriteArrayList<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        WoodlightPersistentState state = WoodlightPersistentState.get(world);
        state.saveEntries(entries != null ? new ArrayList<>(entries) : new ArrayList<>());
    }

    private void scanForSetups(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlockPos playerPos = player.getBlockPos();
            Set<Long> checkedFrames = new HashSet<>();

            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                    for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                        BlockPos pos = playerPos.add(dx, dy, dz);
                        if (!world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) continue;

                        BlockPos above = pos.up();
                        BlockState aboveState = world.getBlockState(above);
                        boolean isPortalBlock = aboveState.isOf(Blocks.NETHER_PORTAL);
                        if (!aboveState.isAir() && !aboveState.isIn(BlockTags.FIRE) && !isPortalBlock) continue;

                        if (isPortalBlock) {
                            handleLitPortalDetection(world, above, checkedFrames);
                            continue;
                        }

                        NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, above);
                        if (helper == null || !helper.isValid()) continue;

                        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
                        BlockPos lowerCorner = accessor.getLowerCorner();
                        Direction.Axis foundAxis = accessor.getAxis();
                        if (lowerCorner == null) continue;

                        long frameKey = lowerCorner.asLong() ^ ((long) foundAxis.ordinal() << 62);
                        if (!checkedFrames.add(frameKey)) continue;

                        if (isTracked(world, helper)) continue;

                        List<BlockPos> interiorBlocks = getInteriorBlocks(helper);
                        double prob = PortalLightProbability.compute(world, interiorBlocks, world.getDifficulty().getId());

                        int attempt = WoodlightPersistentState.get(world).peekNextAttempt();

                        PortalLightEntry entry = new PortalLightEntry(
                                lowerCorner, foundAxis, above, attempt,
                                world.getTime(), world.getSeed(), prob,
                                helper.getWidth(), helper.getHeight());
                        entry.updateSubChunks(interiorBlocks,
                                getFlammableNeighbors(world, interiorBlocks),
                                getReachableLava(world, interiorBlocks));
                        scheduleFireFadeOut(world, entry, interiorBlocks, world.getTime());
                        activeTimers.computeIfAbsent(world.getRegistryKey(), k -> new CopyOnWriteArrayList<>()).add(entry);
                        persistEntries(world);
                    }
                }
            }
        }
    }

    private boolean isTracked(ServerWorld world, NetherPortalBlock.AreaHelper helper) {
        List<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries == null || entries.isEmpty()) return false;

        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
        BlockPos lowerCorner = accessor.getLowerCorner();
        Direction.Axis axis = accessor.getAxis();
        if (lowerCorner == null) return false;

        for (PortalLightEntry entry : entries) {
            if (entry.matchesPortal(lowerCorner, axis)) return true;
        }
        return false;
    }

    private List<BlockPos> getInteriorBlocks(NetherPortalBlock.AreaHelper helper) {
        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
        BlockPos lowerCorner = accessor.getLowerCorner();
        Direction.Axis axis = accessor.getAxis();
        int width = helper.getWidth();
        int height = helper.getHeight();

        Direction negativeDir = (axis == Direction.Axis.X) ? Direction.WEST : Direction.SOUTH;

        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                result.add(lowerCorner.offset(negativeDir, i).up(j).toImmutable());
            }
        }
        return result;
    }

    private List<BlockPos> getFlammableNeighbors(ServerWorld world, List<BlockPos> interiorBlocks) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : interiorBlocks) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.offset(dir);
                if (world.getBlockState(neighbor).getMaterial().isBurnable()) {
                    result.add(neighbor.toImmutable());
                }
            }
        }
        return result;
    }

    /** Flammable blocks in the ±2 zone around interior, used for grouping. */
    private List<BlockPos> getZoneFlammable(ServerWorld world, List<BlockPos> interiorBlocks) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos interior : interiorBlocks) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = interior.add(dx, dy, dz);
                        if (!seen.add(pos)) continue;
                        if (world.getBlockState(pos).getMaterial().isBurnable()) {
                            result.add(pos.toImmutable());
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<BlockPos> getReachableLava(ServerWorld world, List<BlockPos> interiorBlocks) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos airPos : interiorBlocks) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 0; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos checkPos = airPos.add(dx, dy, dz);
                        if (seen.contains(checkPos)) continue;
                        seen.add(checkPos);
                        if (world.getFluidState(checkPos).isIn(FluidTags.LAVA)) {
                            result.add(checkPos.toImmutable());
                        }
                    }
                }
            }
        }
        return result;
    }

    private void handleLitPortalDetection(ServerWorld world, BlockPos portalBlock, Set<Long> checkedFrames) {
        // Walk to find lowerCorner of the lit portal
        BlockPos start = portalBlock;
        Direction.Axis axis = world.getBlockState(portalBlock).get(net.minecraft.state.property.Properties.HORIZONTAL_AXIS);
        Direction negDir = (axis == Direction.Axis.X) ? Direction.WEST : Direction.SOUTH;

        while (world.getBlockState(start.offset(negDir)).isOf(Blocks.NETHER_PORTAL)) {
            start = start.offset(negDir);
        }
        while (world.getBlockState(start.down()).isOf(Blocks.NETHER_PORTAL)) {
            start = start.down();
        }

        long frameKey = start.asLong() ^ ((long) axis.ordinal() << 62);
        if (!checkedFrames.add(frameKey)) return;
        if (isTrackedAt(world, start, axis)) return;

        Direction posDir = negDir.getOpposite();
        int width = 0;
        while (world.getBlockState(start.offset(posDir, width)).isOf(Blocks.NETHER_PORTAL)) width++;
        int height = 0;
        while (world.getBlockState(start.up(height)).isOf(Blocks.NETHER_PORTAL)) height++;

        List<BlockPos> interior = new ArrayList<>();
        List<BlockPos> frame = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                interior.add(start.offset(posDir, i).up(j).toImmutable());
            }
            frame.add(start.offset(posDir, i).down().toImmutable());
            frame.add(start.offset(posDir, i).up(height).toImmutable());
        }
        for (int j = 0; j < height; j++) {
            frame.add(start.offset(negDir).up(j).toImmutable());
            frame.add(start.offset(posDir, width).up(j).toImmutable());
        }

        PortalLightEntry entry = new PortalLightEntry(
                start, axis, start, 0, world.getTime(), world.getSeed(), 0, width, height);
        entry.lit = true;
        entry.cachedInterior = interior;
        entry.cachedFrame = frame;
        activeTimers.computeIfAbsent(world.getRegistryKey(), k -> new CopyOnWriteArrayList<>()).add(entry);
    }

    private boolean isTrackedAt(ServerWorld world, BlockPos lowerCorner, Direction.Axis axis) {
        List<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries == null) return false;
        for (PortalLightEntry entry : entries) {
            if (entry.matchesPortal(lowerCorner, axis)) return true;
        }
        return false;
    }

    private boolean isPortalLit(ServerWorld world, List<BlockPos> interiorBlocks) {
        for (BlockPos pos : interiorBlocks) {
            if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.NETHER_PORTAL)) return true;
        }
        return false;
    }

    private List<BlockPos> getFrameBlocks(NetherPortalBlock.AreaHelper helper) {
        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
        BlockPos lowerCorner = accessor.getLowerCorner();
        Direction.Axis axis = accessor.getAxis();
        int width = helper.getWidth();
        int height = helper.getHeight();

        Direction negativeDir = (axis == Direction.Axis.X) ? Direction.WEST : Direction.SOUTH;
        Direction positiveDir = negativeDir.getOpposite();

        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            result.add(lowerCorner.offset(negativeDir, i).down().toImmutable());
            result.add(lowerCorner.offset(negativeDir, i).up(height).toImmutable());
        }
        for (int j = 0; j < height; j++) {
            result.add(lowerCorner.offset(positiveDir).up(j).toImmutable());
            result.add(lowerCorner.offset(negativeDir, width).up(j).toImmutable());
        }
        return result;
    }

    private void scheduleFireFadeOut(ServerWorld world, PortalLightEntry entry,
                                     List<BlockPos> interiorBlocks, long currentTick) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> fires = new ArrayList<>();
        for (BlockPos interior : interiorBlocks) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 4; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos pos = interior.add(dx, dy, dz);
                        if (!seen.add(pos)) continue;
                        if (!world.getBlockState(pos).isIn(BlockTags.FIRE)) continue;
                        fires.add(pos.toImmutable());
                    }
                }
            }
        }
        if (fires.isEmpty()) return;
        fires.sort(Comparator.comparingLong(BlockPos::asLong));

        int n = fires.size();
        for (int i = 0; i < n; i++) {
            long offset = (n == 1) ? 10L : 1L + ((long) i * 19L) / (n - 1);
            entry.pendingExtinguish.put(fires.get(i), currentTick + offset);
        }
    }

    private List<BlockPos> findFireNear(ServerWorld world, List<BlockPos> interiorBlocks) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos interior : interiorBlocks) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = interior.add(dx, dy, dz);
                        if (!seen.add(pos)) continue;
                        if (world.getBlockState(pos).isIn(BlockTags.FIRE)) result.add(pos.toImmutable());
                    }
                }
            }
        }
        return result;
    }

    private boolean isObsidianFrameIntact(ServerWorld world, PortalLightEntry entry) {
        if (entry.cachedFrame.isEmpty()) return false;
        for (BlockPos pos : entry.cachedFrame) {
            if (!world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) return false;
        }
        return true;
    }

    private boolean isInteriorAllPortalBlocks(ServerWorld world, PortalLightEntry entry) {
        if (entry.cachedInterior.isEmpty()) return false;
        for (BlockPos pos : entry.cachedInterior) {
            if (!world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) return false;
        }
        return true;
    }

    private boolean isNearAnyPlayer(ServerWorld world, BlockPos pos) {
        int viewDist = world.getServer().getPlayerManager().getViewDistance();
        int portalChunkX = pos.getX() >> 4;
        int portalChunkZ = pos.getZ() >> 4;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            int playerChunkX = player.getBlockPos().getX() >> 4;
            int playerChunkZ = player.getBlockPos().getZ() >> 4;
            if (Math.abs(portalChunkX - playerChunkX) <= viewDist
                    && Math.abs(portalChunkZ - playerChunkZ) <= viewDist) {
                return true;
            }
        }
        return false;
    }
}
