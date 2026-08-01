package net.vibzz.woodlightingstandards.portal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.vibzz.woodlightingstandards.Woodlightingstandards;
import net.vibzz.woodlightingstandards.mixin.AreaHelperAccessor;
import net.vibzz.woodlightingstandards.util.PortalLightProbability;
import net.vibzz.woodlightingstandards.util.SeedTimingUtil;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final Set<RegistryKey<World>> loadedWorlds = ConcurrentHashMap.newKeySet();

    private static final int SCAN_RADIUS = 12;
    private static final int SCAN_INTERVAL = 10;
    private static final int SUPPRESS_RADIUS = 5;


    public static WoodlightTracker getInstance() {
        return INSTANCE;
    }

    public void reset() {
        activeTimers.clear();
        loadedWorlds.clear();
    }

    public List<PortalLightEntry> getAllEntries(ServerWorld world) {
        return activeTimers.get(world.getRegistryKey());
    }

    public boolean isInTrackedArea(World world, BlockPos pos) {
        List<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries == null || entries.isEmpty()) return false;

        for (PortalLightEntry entry : entries) {
            if (entry.lit) continue;
            for (BlockPos interior : entry.cachedInterior) {
                if (Math.abs(pos.getX() - interior.getX()) <= SUPPRESS_RADIUS
                        && Math.abs(pos.getY() - interior.getY()) <= SUPPRESS_RADIUS
                        && Math.abs(pos.getZ() - interior.getZ()) <= SUPPRESS_RADIUS) {
                    return true;
                }
            }
        }
        return false;
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
                boolean blocked = helper == null || !helper.isValid();

                if (blocked) {
                    if (isInteriorAllPortalBlocks(world, entry)) {
                        entry.lit = true;
                        continue;
                    }
                    if (!isObsidianFrameIntact(world, entry)) {
                        toRemove.add(entry);
                        continue;
                    }
                }
                entry.blocked = blocked;

                List<BlockPos> interiorBlocks = blocked ? entry.cachedInterior : getInteriorBlocks(helper);

                if (!blocked && isPortalLit(world, interiorBlocks)) {
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

                if (!blocked) {
                    entry.cachedInterior = interiorBlocks;
                    entry.cachedFrame = getFrameBlocks(helper);
                }
                entry.cachedFlammable = flammable;
                entry.cachedLava = lava;
                entry.cachedFirePositions = firePositions;
                entry.cachedFireCount = firePositions.size();
                entry.perTickProbability = blocked ? 0 : PortalLightProbability.compute(
                        world, interiorBlocks, currentDifficulty, entry.pendingExtinguish.keySet());

                entry.scheduler.tick(world, interiorBlocks, currentTick, currentDifficulty,
                        entry.pendingExtinguish.keySet());

                if (!blocked) {
                    activeEntries.add(entry);
                }
            }

            if (!activeEntries.isEmpty()) {
                WoodlightPersistentState state = WoodlightPersistentState.get(world);
                double rate = 0;
                for (PortalLightEntry e : activeEntries) rate += e.perTickProbability;

                double target = SeedTimingUtil.calculateTargetCumulative(world.getSeed(), state.peekNextAttempt());
                double progress = state.getGlobalProgress() + rate;

                if (progress >= target) {
                    PortalLightEntry winner = pickWinner(activeEntries, world.getSeed(), state.peekNextAttempt());
                    NetherPortalBlock.AreaHelper winnerHelper =
                            NetherPortalBlock.createAreaHelper(world, winner.probePos);
                    if (winnerHelper != null && winnerHelper.isValid()) {
                        winnerHelper.createPortal();
                    }
                    winner.lit = true;
                    state.commitAttempt();
                    state.setGlobalProgress(0);
                } else {
                    state.setGlobalProgress(progress);
                }
            }

            if (!toRemove.isEmpty()) {
                entries.removeAll(toRemove);
                persistEntries(world);
            }
        }

        if (world.getTime() % SCAN_INTERVAL == 0) {
            scanForSetups(world);
        }
    }

    private PortalLightEntry pickWinner(List<PortalLightEntry> candidates, long seed, int attempt) {
        List<PortalLightEntry> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingLong((PortalLightEntry e) -> e.lowerCorner.asLong())
                .thenComparingInt(e -> e.axis.ordinal()));

        double total = 0;
        for (PortalLightEntry e : sorted) total += e.perTickProbability;
        if (total <= 0) return sorted.get(0);

        long mixed = mixSeed(seed ^ mixSeed(attempt) ^ 0x57494E4EL);
        double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / ((double) Long.MAX_VALUE + 1.0);
        double threshold = uniform * total;

        double accum = 0;
        for (PortalLightEntry e : sorted) {
            accum += e.perTickProbability;
            if (accum >= threshold) return e;
        }
        return sorted.get(sorted.size() - 1);
    }

    private static long mixSeed(long seed) {
        seed ^= (seed >>> 30);
        seed *= 0xbf58476d1ce4e5b9L;
        seed ^= (seed >>> 27);
        seed *= 0x94d049bb133111ebL;
        seed ^= (seed >>> 31);
        return seed;
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
                        entry.cachedInterior = interiorBlocks;
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
        BlockPos start = portalBlock;
        Direction.Axis axis = world.getBlockState(portalBlock).get(net.minecraft.state.property.Properties.HORIZONTAL_AXIS);
        Direction negDir = (axis == Direction.Axis.X) ? Direction.WEST : Direction.SOUTH;
        Direction posDir = negDir.getOpposite();

        while (world.getBlockState(start.offset(negDir)).isOf(Blocks.NETHER_PORTAL)) {
            start = start.offset(negDir);
        }
        while (world.getBlockState(start.down()).isOf(Blocks.NETHER_PORTAL)) {
            start = start.down();
        }

        int width = 0;
        while (world.getBlockState(start.offset(posDir, width)).isOf(Blocks.NETHER_PORTAL)) width++;
        int height = 0;
        while (world.getBlockState(start.up(height)).isOf(Blocks.NETHER_PORTAL)) height++;

        // positive-most bottom corner, matching AreaHelper.getLowerCorner so dedup works
        BlockPos lowerCorner = start.offset(posDir, width - 1);

        long frameKey = lowerCorner.asLong() ^ ((long) axis.ordinal() << 62);
        if (!checkedFrames.add(frameKey)) return;
        if (isTrackedAt(world, lowerCorner, axis)) return;

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
                lowerCorner, axis, lowerCorner, 0, world.getTime(), world.getSeed(), 0, width, height);
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
                        if (world.getBlockState(pos.down()).isIn(world.getDimension().getInfiniburnBlocks())) continue;
                        fires.add(pos.toImmutable());
                    }
                }
            }
        }
        for (BlockPos pos : fires) {
            entry.pendingExtinguish.put(pos, currentTick + 1 + world.random.nextInt(20));
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
