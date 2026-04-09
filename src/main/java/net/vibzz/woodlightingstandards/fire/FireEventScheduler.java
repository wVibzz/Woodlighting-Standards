package net.vibzz.woodlightingstandards.fire;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.vibzz.woodlightingstandards.util.FlammableBlockUtil;
import net.vibzz.woodlightingstandards.util.LavaReachUtil;
import net.vibzz.woodlightingstandards.util.LavaWeightUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schedules and executes deterministic fire events near portals.
 */
public class FireEventScheduler {

    private static final double LAVA_TICK_CHANCE = 6.0 / 4096.0;
    private static final double AVG_FIRE_TICK_INTERVAL = 34.5;

    private final Map<BlockPos, Long> scheduledFires = new HashMap<>();
    private final Map<BlockPos, Long> scheduledBurnAway = new HashMap<>();
    private final Map<Long, Integer> positionCounters = new HashMap<>();

    public Map<BlockPos, Long> getScheduledFires() { return scheduledFires; }
    public Map<BlockPos, Long> getScheduledBurnAway() { return scheduledBurnAway; }
    public Map<Long, Integer> getPositionCounters() { return positionCounters; }

    private final long worldSeed;
    private final int attempt;
    private final BlockPos portalOrigin;
    private final Direction.Axis portalAxis;
    private final int portalWidth;

    public FireEventScheduler(long worldSeed, int attempt, BlockPos portalOrigin,
                              Direction.Axis portalAxis, int portalWidth) {
        this.worldSeed = worldSeed;
        this.attempt = attempt;
        this.portalOrigin = portalOrigin.toImmutable();
        this.portalAxis = portalAxis;
        this.portalWidth = portalWidth;
    }

    /**
     * Canonical position key, invariant under mirroring and axis rotation.
     */
    private long canonicalKey(BlockPos pos) {
        int dx = pos.getX() - portalOrigin.getX();
        int dy = pos.getY() - portalOrigin.getY();
        int dz = pos.getZ() - portalOrigin.getZ();

        int u, v;
        if (portalAxis == Direction.Axis.X) {
            u = dx;
            v = dz;
        } else {
            u = dz;
            v = dx;
        }

        int uCanonical = Math.min(u, (portalWidth - 1) - u);
        int vCanonical = Math.abs(v);

        return BlockPos.asLong(uCanonical, dy, vCanonical);
    }

    public void tick(ServerWorld world, List<BlockPos> interiorBlocks, long currentTick, int difficulty) {
        Set<BlockPos> interiorSet = new HashSet<>(interiorBlocks);
        validateScheduled(world);
        scheduleLavaFires(world, interiorBlocks, interiorSet, currentTick);
        processScheduledFires(world, interiorSet, currentTick, difficulty);
        processScheduledBurnAway(world, interiorSet, currentTick, difficulty);
    }

    public void clearPreExistingFires(ServerWorld world, List<BlockPos> interiorBlocks) {
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos interior : interiorBlocks) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 4; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos pos = interior.add(dx, dy, dz);
                        if (!seen.add(pos)) continue;
                        if (!world.getBlockState(pos).isIn(net.minecraft.tag.BlockTags.FIRE)) continue;
                        world.setBlockState(pos.toImmutable(), net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
    }

    private void validateScheduled(ServerWorld world) {
        scheduledFires.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (!world.getBlockState(pos).isAir()) return true;
            if (!hasBurnableNeighbor(world, pos)) return true;
            return !hasIgnitionSource(world, pos);
        });

        scheduledBurnAway.entrySet().removeIf(entry ->
                !FlammableBlockUtil.isFlammable(world.getBlockState(entry.getKey())));
    }

    private boolean hasIgnitionSource(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isIn(net.minecraft.tag.BlockTags.FIRE)) return true;
        }

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 0; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    if (world.getFluidState(checkPos).isIn(FluidTags.LAVA)) {
                        if (LavaReachUtil.canLavaReachSlot(world, checkPos, pos)) return true;
                    }
                }
            }
        }

        return false;
    }

    private void scheduleLavaFires(ServerWorld world, List<BlockPos> interiorBlocks, Set<BlockPos> interiorSet, long currentTick) {
        List<BlockPos> fireTargets = findLavaFireTargets(world, interiorBlocks, interiorSet);

        for (BlockPos target : fireTargets) {
            if (scheduledFires.containsKey(target)) continue;
            if (!world.getBlockState(target).isAir()) continue;

            double prob = computeTargetProbability(world, target);
            if (prob <= 0) continue;

            long fireTick = currentTick + probabilityToTicks(prob, target);
            scheduledFires.put(target.toImmutable(), fireTick);
        }
    }

    private List<BlockPos> findLavaFireTargets(ServerWorld world, List<BlockPos> interiorBlocks, Set<BlockPos> interiorSet) {
        List<BlockPos> targets = new ArrayList<>();

        for (BlockPos interior : interiorBlocks) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos candidate = interior.add(dx, dy, dz);
                        if (interiorSet.contains(candidate)) continue;
                        if (!world.getBlockState(candidate).isAir()) continue;
                        if (!hasBurnableNeighbor(world, candidate)) continue;
                        targets.add(candidate.toImmutable());
                    }
                }
            }
        }

        return targets;
    }

    private double computeTargetProbability(ServerWorld world, BlockPos airPos) {
        boolean hasBurnableNeighbor = hasBurnableNeighbor(world, airPos);
        double prob = 0;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 0; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = airPos.add(dx, dy, dz);
                    if (!world.getFluidState(checkPos).isIn(FluidTags.LAVA)) continue;
                    if (!LavaReachUtil.canLavaReachSlot(world, checkPos, airPos)) continue;

                    double weight = LavaWeightUtil.calculateWeight(
                            world, checkPos, airPos, hasBurnableNeighbor);
                    prob += LAVA_TICK_CHANCE * weight;
                }
            }
        }

        return prob;
    }

    private void processScheduledFires(ServerWorld world, Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        List<BlockPos> placed = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Long>> iter = scheduledFires.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iter.next();
            if (currentTick < entry.getValue()) continue;

            BlockPos pos = entry.getKey();
            iter.remove();

            if (!world.getBlockState(pos).isAir()) continue;

            world.setBlockState(pos, AbstractFireBlock.getState(world, pos), 3);
            placed.add(pos);
        }
        for (BlockPos pos : placed) {
            onFirePlaced(world, pos, interiorSet, currentTick, difficulty);
        }
    }

    private void processScheduledBurnAway(ServerWorld world, Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        List<BlockPos> burned = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Long>> iter = scheduledBurnAway.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iter.next();
            if (currentTick < entry.getValue()) continue;

            BlockPos pos = entry.getKey();
            iter.remove();

            BlockState state = world.getBlockState(pos);
            if (!FlammableBlockUtil.isFlammable(state)) continue;

            world.setBlockState(pos, AbstractFireBlock.getState(world, pos), 3);
            burned.add(pos);
        }
        for (BlockPos pos : burned) {
            onFirePlaced(world, pos, interiorSet, currentTick, difficulty);
        }
    }

    private void onFirePlaced(ServerWorld world, BlockPos firePos, Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = firePos.offset(dir);
            if (interiorSet.contains(neighbor)) continue;
            BlockState neighborState = world.getBlockState(neighbor);

            if (FlammableBlockUtil.isFlammable(neighborState) && !scheduledBurnAway.containsKey(neighbor)) {
                int burnTime = BurnAwayTiming.calculateBurnTime(neighborState, worldSeed);
                if (burnTime > 0) {
                    scheduledBurnAway.put(neighbor.toImmutable(), currentTick + burnTime);
                }
            }

            if (neighborState.isAir() && hasBurnableNeighbor(world, neighbor) && !scheduledFires.containsKey(neighbor)) {
                double spreadProb = computeFireSpreadProbability(world, neighbor, difficulty);
                if (spreadProb > 0) {
                    long spreadTick = currentTick + probabilityToTicks(spreadProb, neighbor);
                    scheduledFires.put(neighbor.toImmutable(), spreadTick);
                }
            }
        }
    }

    private double computeFireSpreadProbability(ServerWorld world, BlockPos airPos, int difficulty) {
        int maxBurn = 0;
        for (Direction dir : Direction.values()) {
            int burn = FlammableBlockUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
            if (burn > maxBurn) maxBurn = burn;
        }
        if (maxBurn == 0) return 0;

        double q = (maxBurn + 40.0 + difficulty * 7) / 30.0;
        double perFireTick = Math.min(1.0, (q + 1) / 100.0);
        return perFireTick / AVG_FIRE_TICK_INTERVAL;
    }

    private int probabilityToTicks(double perTickProbability, BlockPos pos) {
        if (perTickProbability <= 0) return Integer.MAX_VALUE;

        long key = canonicalKey(pos);
        int count = positionCounters.merge(key, 1, Integer::sum);

        double expectedTicks = 1.0 / perTickProbability;
        long hash = mixSeed(worldSeed ^ mixSeed(attempt) ^ mixSeed(key) ^ mixSeed(count));
        double uniform = (double) (hash & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;

        return Math.max(1, (int) (-Math.log(1.0 - uniform) * expectedTicks));
    }

    public void loadState(Map<BlockPos, Long> fires, Map<BlockPos, Long> burnAway, Map<Long, Integer> counters) {
        scheduledFires.clear();
        scheduledFires.putAll(fires);
        scheduledBurnAway.clear();
        scheduledBurnAway.putAll(burnAway);
        positionCounters.clear();
        positionCounters.putAll(counters);
    }

    private static boolean hasBurnableNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).getMaterial().isBurnable()) return true;
        }
        return false;
    }

    private static long mixSeed(long seed) {
        seed ^= (seed >>> 30);
        seed *= 0xbf58476d1ce4e5b9L;
        seed ^= (seed >>> 27);
        seed *= 0x94d049bb133111ebL;
        seed ^= (seed >>> 31);
        return seed;
    }
}
