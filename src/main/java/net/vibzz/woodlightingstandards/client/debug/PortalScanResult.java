package net.vibzz.woodlightingstandards.client.debug;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.vibzz.woodlightingstandards.portal.PortalLightEntry;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;
import net.vibzz.woodlightingstandards.util.FlammableBlockUtil;
import net.vibzz.woodlightingstandards.util.LavaReachUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PortalScanResult {
    public boolean hasResults = false;


    public final List<PortalData> portals = new ArrayList<>();


    public final List<BlockPos> portalFrame = new ArrayList<>();
    public final Set<BlockPos> portalInterior = new HashSet<>();
    public final Map<BlockPos, int[]> flammables = new LinkedHashMap<>();
    public final Set<BlockPos> lavaSources = new HashSet<>();
    public final Set<BlockPos> fireBlocks = new HashSet<>();
    public final Set<BlockPos> burnSlots = new HashSet<>();
    public final Set<BlockPos> filledBurnSlots = new HashSet<>();


    public long worldSeed = 0;

    public static class PortalData {
        public Direction.Axis axis;
        public final List<BlockPos> frame = new ArrayList<>();
        public final Set<BlockPos> interior = new HashSet<>();
        public final Set<BlockPos> burnSlots = new HashSet<>();
        public final Set<BlockPos> filledBurnSlots = new HashSet<>();
        public final Set<BlockPos> lavaZone = new HashSet<>();
        public final Set<BlockPos> effectiveLava = new HashSet<>();
        public int maxBurnSlots = 0;
        public int maxLavaZone = 0;


        public boolean lit = false;
        public double perTickProbability = 0.0;
        public double cumulativeProbability = 0.0;
        public double targetCumulative = 0.0;
        public int chunkCount = 0;
        public int fireCount = 0;
        public final Map<BlockPos, Long> scheduledFires = new LinkedHashMap<>();
        public final Map<BlockPos, Long> scheduledBurnAway = new LinkedHashMap<>();

        public boolean timerActive = false;
        public int attempt = 0;
    }

    public void scan(World world, BlockPos center, int radius) {
        clear();

        Set<Long> foundFrames = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (!world.getBlockState(p).isOf(Blocks.OBSIDIAN)) continue;

                    BlockPos above = p.up();
                    if (!isValidInside(world, above)) continue;

                    boolean hasObsSide = world.getBlockState(above.north()).isOf(Blocks.OBSIDIAN)
                            || world.getBlockState(above.south()).isOf(Blocks.OBSIDIAN)
                            || world.getBlockState(above.east()).isOf(Blocks.OBSIDIAN)
                            || world.getBlockState(above.west()).isOf(Blocks.OBSIDIAN);
                    if (!hasObsSide) continue;

                    for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                        PortalData data = tryDetectPortal(world, above, axis);
                        if (data == null) continue;

                
                        BlockPos lc = findLowerCorner(data);
                        long key = lc.asLong() ^ ((long) data.axis.ordinal() << 62);
                        if (!foundFrames.add(key)) continue;

                        if (!data.lit) {
                            calculateBurnSlots(data);
                            calculateLavaZone(data);
                            scanSurroundings(world, data);
                        }

                        portals.add(data);
                    }
                }
            }
        }


        for (PortalData pd : portals) {
            if (pd.lit) continue;
            portalFrame.addAll(pd.frame);
            portalInterior.addAll(pd.interior);
            burnSlots.addAll(pd.burnSlots);
            filledBurnSlots.addAll(pd.filledBurnSlots);
        }


        updateTimerInfo();

        hasResults = true;
    }

    public void updateTimerInfo() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() == null) return;

        ServerWorld serverWorld = mc.getServer().getOverworld();
        if (serverWorld == null) return;

        worldSeed = serverWorld.getSeed();

        for (PortalData pd : portals) {
            PortalLightEntry entry = WoodlightTracker.getInstance().getEntryNear(serverWorld, pd.interior);
            if (entry != null) {
                pd.timerActive = entry.perTickProbability > 0;
                pd.attempt = entry.attempt;
                pd.perTickProbability = entry.perTickProbability;
                pd.cumulativeProbability = entry.cumulativeProbability;
                pd.targetCumulative = entry.targetCumulative;
                pd.chunkCount = entry.portalSubChunks.size();
                pd.scheduledFires.clear();
                pd.scheduledFires.putAll(entry.fireScheduler.getScheduledFires());
                pd.scheduledBurnAway.clear();
                pd.scheduledBurnAway.putAll(entry.fireScheduler.getScheduledBurnAway());
            } else {
                pd.timerActive = false;
            }
        }
    }

    private PortalData tryDetectPortal(World world, BlockPos bottomLeft, Direction.Axis axis) {
        Direction horiz = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction horizNeg = horiz.getOpposite();

        BlockPos start = bottomLeft;
        int walked = 0;
        while (walked < 21 && isValidInside(world, start.offset(horizNeg))) {
            start = start.offset(horizNeg);
            walked++;
        }

        walked = 0;
        while (walked < 21 && isValidInside(world, start.down())) {
            start = start.down();
            walked++;
        }

        int interiorW = 0;
        while (interiorW < 21 && isValidInside(world, start.offset(horiz, interiorW))) {
            interiorW++;
        }
        if (interiorW < 2) return null;

        int interiorH = 0;
        while (interiorH < 21 && isValidInside(world, start.up(interiorH))) {
            interiorH++;
        }
        if (interiorH < 3) return null;


        for (int x = 0; x < interiorW; x++) {
            for (int y = 0; y < interiorH; y++) {
                if (!isValidInside(world, start.offset(horiz, x).up(y))) return null;
            }
        }


        for (int x = 0; x < interiorW; x++) {
            if (!world.getBlockState(start.offset(horiz, x).down()).isOf(Blocks.OBSIDIAN)) return null;
            if (!world.getBlockState(start.offset(horiz, x).up(interiorH)).isOf(Blocks.OBSIDIAN)) return null;
        }
        for (int y = 0; y < interiorH; y++) {
            if (!world.getBlockState(start.offset(horizNeg).up(y)).isOf(Blocks.OBSIDIAN)) return null;
            if (!world.getBlockState(start.offset(horiz, interiorW).up(y)).isOf(Blocks.OBSIDIAN)) return null;
        }

        PortalData data = new PortalData();
        data.axis = axis;

        boolean allPortalBlocks = true;
        for (int x = 0; x < interiorW; x++) {
            for (int y = 0; y < interiorH; y++) {
                BlockPos interiorPos = start.offset(horiz, x).up(y).toImmutable();
                data.interior.add(interiorPos);
                if (!world.getBlockState(interiorPos).isOf(Blocks.NETHER_PORTAL)) {
                    allPortalBlocks = false;
                }
            }
        }
        data.lit = allPortalBlocks;
        for (int x = 0; x < interiorW; x++) {
            data.frame.add(start.offset(horiz, x).down().toImmutable());
            data.frame.add(start.offset(horiz, x).up(interiorH).toImmutable());
        }
        for (int y = 0; y < interiorH; y++) {
            data.frame.add(start.offset(horizNeg).up(y).toImmutable());
            data.frame.add(start.offset(horiz, interiorW).up(y).toImmutable());
        }

        return data;
    }

    private BlockPos findLowerCorner(PortalData data) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : data.interior) {
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
        }
        return new BlockPos(minX, minY, minZ);
    }

    private void calculateBurnSlots(PortalData data) {
        Direction face1, face2;
        if (data.axis == Direction.Axis.X) {
            face1 = Direction.NORTH;
            face2 = Direction.SOUTH;
        } else {
            face1 = Direction.EAST;
            face2 = Direction.WEST;
        }

        for (BlockPos interior : data.interior) {
            BlockPos slot1 = interior.offset(face1).toImmutable();
            BlockPos slot2 = interior.offset(face2).toImmutable();

            if (!data.frame.contains(slot1) && !data.interior.contains(slot1)) {
                data.burnSlots.add(slot1);
            }
            if (!data.frame.contains(slot2) && !data.interior.contains(slot2)) {
                data.burnSlots.add(slot2);
            }
        }

        data.maxBurnSlots = data.burnSlots.size();
    }

    private void calculateLavaZone(PortalData data) {
        if (data.burnSlots.isEmpty()) {
            data.maxLavaZone = 0;
            return;
        }

        for (BlockPos slot : data.burnSlots) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 0; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos candidate = slot.add(dx, dy, dz).toImmutable();
                        if (!data.frame.contains(candidate) && !data.interior.contains(candidate)
                                && !data.burnSlots.contains(candidate)) {
                            data.lavaZone.add(candidate);
                        }
                    }
                }
            }
        }

        data.maxLavaZone = data.lavaZone.size();
    }

    private void scanSurroundings(World world, PortalData data) {

        for (BlockPos slot : data.burnSlots) {
            BlockState state = world.getBlockState(slot);
            int burn = FlammableBlockUtil.getBurnChance(state);
            if (burn > 0) {
                data.filledBurnSlots.add(slot);
                flammables.put(slot, new int[]{burn, FlammableBlockUtil.getSpreadChance(state)});
            }
        }

        for (BlockPos zonePos : data.lavaZone) {
            if (world.getFluidState(zonePos).isIn(FluidTags.LAVA)) {
                for (BlockPos slot : data.filledBurnSlots) {
                    if (LavaReachUtil.canLavaReachSlot(world, zonePos, slot)) {
                        data.effectiveLava.add(zonePos);
                        lavaSources.add(zonePos);
                        break;
                    }
                }
            }
        }


        Set<BlockPos> scannedFire = new HashSet<>();
        for (BlockPos interior : data.interior) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = interior.add(dx, dy, dz).toImmutable();
                        if (scannedFire.contains(pos)) continue;
                        scannedFire.add(pos);
                        if (world.getBlockState(pos).isIn(BlockTags.FIRE)) {
                            fireBlocks.add(pos);
                            data.fireCount++;
                        }
                    }
                }
            }
        }
    }

    private static boolean isValidInside(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isAir() || state.isIn(BlockTags.FIRE) || state.isOf(Blocks.NETHER_PORTAL);
    }

    public void clear() {
        hasResults = false;
        portals.clear();
        portalFrame.clear();
        portalInterior.clear();
        flammables.clear();
        lavaSources.clear();
        fireBlocks.clear();
        burnSlots.clear();
        filledBurnSlots.clear();
        worldSeed = 0;
    }
}
