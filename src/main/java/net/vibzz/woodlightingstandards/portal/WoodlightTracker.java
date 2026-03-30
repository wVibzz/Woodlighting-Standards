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
import net.vibzz.woodlightingstandards.mixin.AreaHelperAccessor;
import net.vibzz.woodlightingstandards.util.FlammableBlockUtil;
import net.vibzz.woodlightingstandards.util.LavaReachUtil;
import net.vibzz.woodlightingstandards.util.LavaWeightUtil;
import net.vibzz.woodlightingstandards.util.SeedTimingUtil;

import java.util.ArrayList;
import java.util.HashSet;
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

    public static WoodlightTracker getInstance() {
        return INSTANCE;
    }

    public boolean hasActiveTimer(ServerWorld world, BlockPos pos) {
        List<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries == null || entries.isEmpty()) return false;

        NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, pos);
        if (helper == null || !helper.isValid()) return false;

        return isTracked(world, helper);
    }

    public PortalLightEntry getEntryNear(ServerWorld world, Set<BlockPos> interiorPositions) {
        List<PortalLightEntry> entries = activeTimers.get(world.getRegistryKey());
        if (entries == null || entries.isEmpty()) return null;

        for (PortalLightEntry entry : entries) {
            for (BlockPos pos : interiorPositions) {
                NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, pos);
                if (helper == null || !helper.isValid()) continue;

                AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
                BlockPos lowerCorner = accessor.getLowerCorner();
                Direction.Axis axis = accessor.getAxis();
                if (lowerCorner != null && entry.matchesPortal(lowerCorner, axis)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public void tick(ServerWorld world) {
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
            List<PortalLightEntry> toRemove = new ArrayList<>();
            int currentDifficulty = world.getDifficulty().getId();

            for (PortalLightEntry entry : entries) {
                NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, entry.probePos);
                if (helper == null || !helper.isValid()) {
                    toRemove.add(entry);
                    continue;
                }

                if (lacksFlammableBlockNearFrame(world, helper) || lacksIgnitionSourceNearFrame(world, helper)) {
                    toRemove.add(entry);
                    continue;
                }

                double setupScore = calculateSetupScore(world, helper);
                double lavaScore = calculateLavaScore(world, helper);
                entry.recalculate(currentDifficulty, setupScore, lavaScore);

                if (currentTick >= entry.targetTick) {
                    helper.createPortal();
                    WoodlightPersistentState.get(world).commitAttempt();
                    toRemove.add(entry);
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
                        if (!aboveState.isAir() && !aboveState.isIn(BlockTags.FIRE)) continue;

                        NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, above);
                        if (helper == null || !helper.isValid()) continue;

                        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
                        BlockPos lowerCorner = accessor.getLowerCorner();
                        Direction.Axis foundAxis = accessor.getAxis();
                        if (lowerCorner == null) continue;

                        long frameKey = lowerCorner.asLong() ^ ((long) foundAxis.ordinal() << 62);
                        if (!checkedFrames.add(frameKey)) continue;

                        if (isTracked(world, helper)) continue;

                        if (lacksFlammableBlockNearFrame(world, helper)) continue;
                        if (lacksIgnitionSourceNearFrame(world, helper)) continue;

                        int attempt = WoodlightPersistentState.get(world).peekNextAttempt();

                        PortalLightEntry entry = new PortalLightEntry(
                                lowerCorner, foundAxis, above, attempt,
                                world.getTime(), world.getSeed(), world.getDifficulty().getId());
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

    private boolean lacksFlammableBlockNearFrame(ServerWorld world, NetherPortalBlock.AreaHelper helper) {
        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
        BlockPos lowerCorner = accessor.getLowerCorner();
        Direction.Axis axis = accessor.getAxis();
        int width = helper.getWidth();
        int height = helper.getHeight();

        Direction negativeDir = (axis == Direction.Axis.X) ? Direction.WEST : Direction.SOUTH;
        Direction perpPositive, perpNegative;
        if (axis == Direction.Axis.X) {
            perpPositive = Direction.SOUTH;
            perpNegative = Direction.NORTH;
        } else {
            perpPositive = Direction.EAST;
            perpNegative = Direction.WEST;
        }

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                BlockPos interior = lowerCorner.offset(negativeDir, i).up(j);
                if (isBurnable(world, interior.offset(perpPositive))) return false;
                if (isBurnable(world, interior.offset(perpNegative))) return false;
                if (j == 0 && isBurnable(world, interior.down())) return false;
            }
        }

        return true;
    }

    private boolean lacksIgnitionSourceNearFrame(ServerWorld world, NetherPortalBlock.AreaHelper helper) {
        List<BlockPos> filledBurnSlots = getFilledBurnSlots(world, helper);
        if (filledBurnSlots.isEmpty()) return true;

        for (BlockPos burnSlot : filledBurnSlots) {
            if (hasIgnitionSourceNearSlot(world, burnSlot)) return false;
        }

        return true;
    }

    private List<BlockPos> getFilledBurnSlots(ServerWorld world, NetherPortalBlock.AreaHelper helper) {
        AreaHelperAccessor accessor = (AreaHelperAccessor) helper;
        BlockPos lowerCorner = accessor.getLowerCorner();
        Direction.Axis axis = accessor.getAxis();
        int width = helper.getWidth();
        int height = helper.getHeight();

        Direction negativeDir = (axis == Direction.Axis.X) ? Direction.WEST : Direction.SOUTH;
        Direction perpPositive, perpNegative;
        if (axis == Direction.Axis.X) {
            perpPositive = Direction.SOUTH;
            perpNegative = Direction.NORTH;
        } else {
            perpPositive = Direction.EAST;
            perpNegative = Direction.WEST;
        }

        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                BlockPos interior = lowerCorner.offset(negativeDir, i).up(j);
                BlockPos slot1 = interior.offset(perpPositive);
                BlockPos slot2 = interior.offset(perpNegative);
                if (isBurnable(world, slot1)) result.add(slot1.toImmutable());
                if (isBurnable(world, slot2)) result.add(slot2.toImmutable());
            }
        }
        return result;
    }

    private double calculateSetupScore(ServerWorld world, NetherPortalBlock.AreaHelper helper) {
        List<BlockPos> interiorBlocks = getInteriorBlocks(helper);

        int[] airBlockBurnChances = new int[interiorBlocks.size()];
        for (int i = 0; i < interiorBlocks.size(); i++) {
            BlockPos airPos = interiorBlocks.get(i);
            int maxBurn = 0;
            for (Direction dir : Direction.values()) {
                int burn = FlammableBlockUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
                if (burn > maxBurn) maxBurn = burn;
            }
            airBlockBurnChances[i] = maxBurn;
        }

        return SeedTimingUtil.calculateSetupScore(airBlockBurnChances);
    }

    private double calculateLavaScore(ServerWorld world, NetherPortalBlock.AreaHelper helper) {
        List<BlockPos> interiorBlocks = getInteriorBlocks(helper);

        double totalWeight = 0;

        for (BlockPos airPos : interiorBlocks) {
            int maxBurn = 0;
            for (Direction dir : Direction.values()) {
                int burn = FlammableBlockUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
                if (burn > maxBurn) maxBurn = burn;
            }
            if (maxBurn == 0) continue;

            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 0; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos checkPos = airPos.add(dx, dy, dz);
                        if (!world.getFluidState(checkPos).isIn(FluidTags.LAVA)) continue;
                        if (!LavaReachUtil.canLavaReachSlot(world, checkPos, airPos)) continue;

                        double weight = LavaWeightUtil.calculateWeight(checkPos, airPos);
                        totalWeight += weight;
                    }
                }
            }
        }

        return SeedTimingUtil.calculateLavaScore(totalWeight);
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

    private boolean hasIgnitionSourceNearSlot(ServerWorld world, BlockPos slotPos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(slotPos.offset(dir)).isIn(BlockTags.FIRE)) return true;
        }

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 0; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = slotPos.add(dx, dy, dz);
                    if (world.getFluidState(checkPos).isIn(FluidTags.LAVA)) {
                        if (LavaReachUtil.canLavaReachSlot(world, checkPos, slotPos)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isBurnable(ServerWorld world, BlockPos pos) {
        return FlammableBlockUtil.isFlammable(world.getBlockState(pos));
    }
}
