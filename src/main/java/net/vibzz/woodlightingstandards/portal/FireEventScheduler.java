package net.vibzz.woodlightingstandards.portal;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.vibzz.woodlightingstandards.util.FireUtil;
import net.vibzz.woodlightingstandards.util.LavaUtil;
import net.vibzz.woodlightingstandards.util.SeedUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class FireEventScheduler {


    private final Map<BlockPos, ScheduledFire> scheduledLavaFires = new HashMap<>();
    private final Map<BlockPos, ScheduledFire> scheduledSpreadFires = new HashMap<>();
    private final Map<BlockPos, Long> scheduledBurnAway = new HashMap<>();
    private final Map<Long, Integer> positionCounters = new HashMap<>();
    private final Map<BlockPos, TrackedFire> trackedFires = new HashMap<>();
    private final Map<Long, Integer> fireSeedCounters = new HashMap<>();
    private final Map<Long, Integer> burnOutcomeCounters = new HashMap<>();
    private final Map<Long, Integer> spreadAgeCounters = new HashMap<>();
    private int lastDifficulty = -1;
    
    private static final class ScheduledFire {
        final int resistance;
        final int sourceAge;
        final boolean humid;
        final boolean fromSpread;
        long tick;
        ScheduledFire(int resistance, int sourceAge, boolean humid, boolean fromSpread) {
            this.resistance = resistance;
            this.sourceAge = sourceAge;
            this.humid = humid;
            this.fromSpread = fromSpread;
        }
    }

    private static final class TrackedFire {
        final Random rng;
        int age;
        long nextTick;
        TrackedFire(Random rng, long nextTick) {
            this.rng = rng;
            this.nextTick = nextTick;
        }
    }

    private static final ThreadLocal<Boolean> SUPPRESS_FIRE_PORTAL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isSuppressingFirePortal() {
        return SUPPRESS_FIRE_PORTAL.get();
    }

    private void placeFireSuppressed(ServerWorld world, BlockPos pos, int age, long currentTick) {
        SUPPRESS_FIRE_PORTAL.set(Boolean.TRUE);
        try {
            BlockState fireState = AbstractFireBlock.getState(world, pos);
            if (age > 0 && fireState.contains(FireBlock.AGE)) {
                fireState = fireState.with(FireBlock.AGE, age);
            }
            world.setBlockState(pos, fireState, 3);
            registerFire(pos, age, currentTick);
        } finally {
            SUPPRESS_FIRE_PORTAL.set(Boolean.FALSE);
        }
    }

    private void registerFire(BlockPos pos, int age, long currentTick) {
        BlockPos immut = pos.toImmutable();
        if (trackedFires.containsKey(immut)) return;
        long key = canonicalKey(immut);
        int count = fireSeedCounters.merge(key, 1, Integer::sum);
        Random rng = new Random(SeedUtil.mixSeed(worldSeed ^ SeedUtil.mixSeed(attempt) ^ SeedUtil.mixSeed(key) ^ SeedUtil.mixSeed(count) ^ 0x4655454CL));
        TrackedFire fire = new TrackedFire(rng, currentTick + 30 + rng.nextInt(10));
        fire.age = age;
        trackedFires.put(immut, fire);
    }

    private int adjacentFireAge(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            TrackedFire fire = trackedFires.get(pos.offset(dir));
            if (fire != null) return fire.age;
        }
        return 0;
    }

    private Random seededRandom(BlockPos pos, Map<Long, Integer> counters, long salt) {
        long key = canonicalKey(pos);
        int count = counters.merge(key, 1, Integer::sum);
        return new Random(SeedUtil.mixSeed(worldSeed ^ SeedUtil.mixSeed(attempt)
                ^ SeedUtil.mixSeed(key) ^ SeedUtil.mixSeed(count) ^ salt));
    }

    public Map<BlockPos, Long> getScheduledLavaFires() { return tickView(scheduledLavaFires); }
    public Map<BlockPos, Long> getScheduledSpreadFires() { return tickView(scheduledSpreadFires); }
    public Map<BlockPos, Long> getScheduledBurnAway() { return scheduledBurnAway; }

    private static Map<BlockPos, Long> tickView(Map<BlockPos, ScheduledFire> map) {
        Map<BlockPos, Long> view = new HashMap<>();
        map.forEach((pos, scheduled) -> view.put(pos, scheduled.tick));
        return view;
    }

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

    /** Position key invariant under mirroring and axis rotation. */
    private long canonicalKey(BlockPos pos) {
        int relX = pos.getX() - portalOrigin.getX();
        int relY = pos.getY() - portalOrigin.getY();
        int relZ = pos.getZ() - portalOrigin.getZ();

        int alongWidth, depth;
        if (portalAxis == Direction.Axis.X) {
            alongWidth = relX;
            depth = relZ;
        } else {
            alongWidth = relZ;
            depth = relX;
        }

        int mirroredWidth = Math.min(alongWidth, (portalWidth - 1) - alongWidth);
        int mirroredDepth = Math.abs(depth);

        return BlockPos.asLong(mirroredWidth, relY, mirroredDepth);
    }

    /** @param excludePendingFires fires fading out post-detection that should not be integrated. */
    public void tick(ServerWorld world, List<BlockPos> interiorBlocks, long currentTick, int difficulty,
                     Set<BlockPos> excludePendingFires) {
        Set<BlockPos> interiorSet = new HashSet<>(interiorBlocks);

        if (lastDifficulty != -1 && lastDifficulty != difficulty) {
            rescheduleSpreadFires(world, currentTick, difficulty);
        }
        lastDifficulty = difficulty;

        validateScheduled(world);
        integrateExternalFires(world, interiorBlocks, interiorSet, currentTick, difficulty, excludePendingFires);
        tickFires(world, currentTick);
        scheduleLavaFires(world, interiorBlocks, interiorSet, currentTick);
        processScheduledLavaFires(world, interiorSet, currentTick, difficulty);
        processScheduledSpreadFires(world, interiorSet, currentTick, difficulty);
        processScheduledBurnAway(world, interiorSet, currentTick, difficulty);
    }

    private void integrateExternalFires(ServerWorld world, List<BlockPos> interiorBlocks,
                                        Set<BlockPos> interiorSet, long currentTick, int difficulty,
                                        Set<BlockPos> excludePendingFires) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> newlyDiscovered = new ArrayList<>();
        for (BlockPos interior : interiorBlocks) {
            for (int offsetX = -5; offsetX <= 5; offsetX++) {
                for (int offsetY = -5; offsetY <= 5; offsetY++) {
                    for (int offsetZ = -5; offsetZ <= 5; offsetZ++) {
                        BlockPos pos = interior.add(offsetX, offsetY, offsetZ);
                        if (!seen.add(pos)) continue;
                        if (interiorSet.contains(pos)) continue;
                        BlockState state = world.getBlockState(pos);
                        if (!state.isIn(net.minecraft.tag.BlockTags.FIRE)) continue;
                        BlockPos immut = pos.toImmutable();
                        if (trackedFires.containsKey(immut)) continue;
                        if (!excludePendingFires.isEmpty() && excludePendingFires.contains(immut)) continue;
                        registerFire(immut, state.contains(FireBlock.AGE) ? state.get(FireBlock.AGE) : 0, currentTick);
                        newlyDiscovered.add(immut);
                    }
                }
            }
        }
        for (BlockPos firePos : newlyDiscovered) {
            onFirePlaced(world, firePos, interiorSet, currentTick, difficulty);
        }
    }

    private void tickFires(ServerWorld world, long currentTick) {
        if (trackedFires.isEmpty()) return;
        List<BlockPos> positions = new ArrayList<>(trackedFires.keySet());
        positions.sort(Comparator.comparingLong(BlockPos::asLong));

        for (BlockPos pos : positions) {
            TrackedFire fire = trackedFires.get(pos);
            if (fire == null) continue;

            if (!world.getBlockState(pos).isIn(net.minecraft.tag.BlockTags.FIRE)) {
                trackedFires.remove(pos);
                continue;
            }
            if (currentTick < fire.nextTick) continue;

            BlockPos below = pos.down();
            BlockState belowState = world.getBlockState(below);
            boolean infiniburn = belowState.isIn(world.getDimension().getInfiniburnBlocks());

            if (!infiniburn) {
                if (world.isRaining() && FireUtil.isRainingAround(world, pos)
                        && fire.rng.nextFloat() < 0.2f + fire.age * 0.03f) {
                    extinguish(world, pos);
                    continue;
                }
            }

            int ageBefore = fire.age;
            fire.age = Math.min(15, fire.age + fire.rng.nextInt(3) / 2);
            if (fire.age != ageBefore) {
                BlockState fireState = world.getBlockState(pos);
                if (fireState.contains(FireBlock.AGE)) {
                    world.setBlockState(pos, fireState.with(FireBlock.AGE, fire.age), 4);
                }
            }

            if (!infiniburn) {
                if (!hasFlammableNeighbor(world, pos)) {
                    if (!belowState.isSideSolidFullSquare(world, below, Direction.UP) || ageBefore > 3) {
                        extinguish(world, pos);
                        continue;
                    }
                } else if (ageBefore == 15 && fire.rng.nextInt(4) == 0
                        && FireUtil.getBurnChance(belowState) == 0) {
                    extinguish(world, pos);
                    continue;
                }
            }

            fire.nextTick = currentTick + 30 + fire.rng.nextInt(10);
        }
    }

    private void extinguish(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
        trackedFires.remove(pos);
    }


    private void rescheduleSpreadFires(ServerWorld world, long currentTick, int newDifficulty) {
        Iterator<Map.Entry<BlockPos, ScheduledFire>> iter = scheduledSpreadFires.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<BlockPos, ScheduledFire> entry = iter.next();
            BlockPos pos = entry.getKey();
            ScheduledFire scheduled = entry.getValue();
            double spreadProb = computeFireSpreadProbability(world, pos, newDifficulty, scheduled);
            if (spreadProb <= 0) {
                iter.remove();
                continue;
            }
            scheduled.tick = currentTick + probabilityToTicks(spreadProb, pos);
        }
    }

    private void validateScheduled(ServerWorld world) {
        scheduledLavaFires.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (!world.getBlockState(pos).isAir()) return true;
            if (!FireUtil.hasBurnableNeighbor(world, pos)) return true;
            return computeTargetProbability(world, pos) <= 0;
        });

        scheduledSpreadFires.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (!world.getBlockState(pos).isAir()) return true;
            if (!FireUtil.hasBurnableNeighbor(world, pos)) return true;
            return !hasFireNeighbor(world, pos);
        });

        scheduledBurnAway.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (!FireUtil.isFlammable(world.getBlockState(pos))) return true;
            return !hasFireNeighbor(world, pos);
        });
    }

    private void scheduleLavaFires(ServerWorld world, List<BlockPos> interiorBlocks, Set<BlockPos> interiorSet, long currentTick) {
        List<BlockPos> fireTargets = findLavaFireTargets(world, interiorBlocks, interiorSet);

        for (BlockPos target : fireTargets) {
            if (scheduledLavaFires.containsKey(target) || scheduledSpreadFires.containsKey(target)) continue;
            if (!world.getBlockState(target).isAir()) continue;

            double prob = computeTargetProbability(world, target);
            if (prob <= 0) continue;

            ScheduledFire scheduled = new ScheduledFire(0, 0, false, false);
            scheduled.tick = currentTick + probabilityToTicks(prob, target);
            scheduledLavaFires.put(target.toImmutable(), scheduled);
        }
    }

    private List<BlockPos> findLavaFireTargets(ServerWorld world, List<BlockPos> interiorBlocks, Set<BlockPos> interiorSet) {
        List<BlockPos> targets = new ArrayList<>();

        for (BlockPos interior : interiorBlocks) {
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetY = -1; offsetY <= 2; offsetY++) {
                    for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                        BlockPos candidate = interior.add(offsetX, offsetY, offsetZ);
                        if (interiorSet.contains(candidate)) continue;
                        if (!world.getBlockState(candidate).isAir()) continue;
                        if (!FireUtil.hasBurnableNeighbor(world, candidate)) continue;
                        targets.add(candidate.toImmutable());
                    }
                }
            }
        }

        return targets;
    }

    private double computeTargetProbability(ServerWorld world, BlockPos airPos) {
        boolean hasBurnableNeighbor = FireUtil.hasBurnableNeighbor(world, airPos);
        double prob = 0;

        for (int offsetX = -3; offsetX <= 3; offsetX++) {
            for (int offsetY = -3; offsetY <= 0; offsetY++) {
                for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                    BlockPos lavaPos = airPos.add(offsetX, offsetY, offsetZ);
                    if (!world.getFluidState(lavaPos).isIn(FluidTags.LAVA)) continue;
                    if (!LavaUtil.canReach(world, lavaPos, airPos)) continue;

                    double weight = LavaUtil.ignitionChance(
                            world, lavaPos, airPos, hasBurnableNeighbor);
                    prob += FireUtil.LAVA_TICK_CHANCE * weight;
                }
            }
        }

        return prob;
    }

    private void processScheduledLavaFires(ServerWorld world, Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        processFireMap(scheduledLavaFires, world, interiorSet, currentTick, difficulty);
    }

    private void processScheduledSpreadFires(ServerWorld world, Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        processFireMap(scheduledSpreadFires, world, interiorSet, currentTick, difficulty);
    }

    private void processFireMap(Map<BlockPos, ScheduledFire> map, ServerWorld world,
                                Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        List<BlockPos> placed = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, ScheduledFire>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<BlockPos, ScheduledFire> entry = iter.next();
            ScheduledFire scheduled = entry.getValue();
            if (currentTick < scheduled.tick) continue;

            BlockPos pos = entry.getKey();
            iter.remove();

            if (!world.getBlockState(pos).isAir()) continue;

            int age = 0;
            if (scheduled.fromSpread) {
                Random rng = seededRandom(pos, spreadAgeCounters, 0x53505244L);
                age = Math.min(15, scheduled.sourceAge + rng.nextInt(5) / 4);
            }

            placeFireSuppressed(world, pos, age, currentTick);
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
            if (!FireUtil.isFlammable(state)) continue;

            int sourceAge = adjacentFireAge(pos);
            Random rng = seededRandom(pos, burnOutcomeCounters, 0x4255524EL);
            if (rng.nextInt(sourceAge + 10) < 5 && !world.hasRain(pos)) {
                placeFireSuppressed(world, pos, Math.min(sourceAge + rng.nextInt(5) / 4, 15), currentTick);
                burned.add(pos);
            } else {
                world.removeBlock(pos, false);
            }
        }
        for (BlockPos pos : burned) {
            onFirePlaced(world, pos, interiorSet, currentTick, difficulty);
        }
    }

    private void onFirePlaced(ServerWorld world, BlockPos firePos, Set<BlockPos> interiorSet, long currentTick, int difficulty) {
        TrackedFire source = trackedFires.get(firePos);
        int sourceAge = source != null ? source.age : 0;
        boolean humid = world.hasHighHumidity(firePos);

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = firePos.offset(dir);
            if (interiorSet.contains(neighbor)) continue;
            BlockState neighborState = world.getBlockState(neighbor);

            if (FireUtil.isFlammable(neighborState) && !scheduledBurnAway.containsKey(neighbor)) {
                int spreadFactor = (dir.getAxis().isVertical() ? 250 : 300) - (humid ? 50 : 0);
                double burnChancePerTick = FireUtil.getSpreadChance(neighborState)
                        / (spreadFactor * FireUtil.AVG_FIRE_TICK_INTERVAL);
                if (burnChancePerTick > 0) {
                    scheduledBurnAway.put(neighbor.toImmutable(),
                            currentTick + probabilityToTicks(burnChancePerTick, neighbor));
                }
            }
        }

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                for (int offsetY = -1; offsetY <= 4; offsetY++) {
                    if (offsetX == 0 && offsetY == 0 && offsetZ == 0) continue;

                    BlockPos target = firePos.add(offsetX, offsetY, offsetZ);
                    if (interiorSet.contains(target)) continue;
                    if (!world.getBlockState(target).isAir()) continue;
                    if (scheduledLavaFires.containsKey(target) || scheduledSpreadFires.containsKey(target)) continue;

                    int spreadResistance = 100;
                    if (offsetY > 1) spreadResistance += (offsetY - 1) * 100;

                    ScheduledFire scheduled = new ScheduledFire(spreadResistance, sourceAge, humid, true);
                    double spreadProb = computeFireSpreadProbability(world, target, difficulty, scheduled);
                    if (spreadProb > 0) {
                        scheduled.tick = currentTick + probabilityToTicks(spreadProb, target);
                        scheduledSpreadFires.put(target.toImmutable(), scheduled);
                    }
                }
            }
        }
    }

    private double computeFireSpreadProbability(ServerWorld world, BlockPos airPos, int difficulty, ScheduledFire scheduled) {
        int maxBurnChance = 0;
        for (Direction dir : Direction.values()) {
            int burnChance = FireUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
            if (burnChance > maxBurnChance) maxBurnChance = burnChance;
        }
        if (maxBurnChance == 0) return 0;
        if (world.isRaining() && FireUtil.isRainingAround(world, airPos)) return 0;

        int igniteChance = (maxBurnChance + 40 + difficulty * 7) / (scheduled.sourceAge + 30);
        if (scheduled.humid) igniteChance /= 2;
        if (igniteChance <= 0) return 0;

        double perFireTick = Math.min(1.0, (igniteChance + 1.0) / scheduled.resistance);
        return perFireTick / FireUtil.AVG_FIRE_TICK_INTERVAL;
    }

    private int probabilityToTicks(double perTickProbability, BlockPos pos) {
        if (perTickProbability <= 0) return Integer.MAX_VALUE;

        long key = canonicalKey(pos);
        int count = positionCounters.merge(key, 1, Integer::sum);

        double expectedTicks = 1.0 / perTickProbability;
        long hash = SeedUtil.mixSeed(worldSeed ^ SeedUtil.mixSeed(attempt)
                ^ SeedUtil.mixSeed(key) ^ SeedUtil.mixSeed(count));

        return Math.max(1, (int) (-Math.log(1.0 - SeedUtil.toUniform(hash)) * expectedTicks));
    }

    private static boolean hasFlammableNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (FireUtil.isFlammable(world.getBlockState(pos.offset(dir)))) return true;
        }
        return false;
    }

    private static boolean hasFireNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isIn(net.minecraft.tag.BlockTags.FIRE)) return true;
        }
        return false;
    }

}
