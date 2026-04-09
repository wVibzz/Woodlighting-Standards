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
import net.vibzz.woodlightingstandards.mixin.AreaHelperAccessor;
import net.vibzz.woodlightingstandards.util.PortalLightProbability;

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

    /**
     * Check if a chunk contains an active portal setup.
     * Used by fire suppression mixins.
     */
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
                if (!world.getChunkManager().isChunkLoaded(entry.probePos.getX() >> 4, entry.probePos.getZ() >> 4)) {
                    continue;
                }

                NetherPortalBlock.AreaHelper helper = NetherPortalBlock.createAreaHelper(world, entry.probePos);
                if (helper == null || !helper.isValid()) {
                    toRemove.add(entry);
                    continue;
                }

                List<BlockPos> interiorBlocks = getInteriorBlocks(helper);

                // If the portal is already lit, drop the entry and skip processing
                if (isPortalLit(world, interiorBlocks)) {
                    toRemove.add(entry);
                    continue;
                }

                entry.fireScheduler.tick(world, interiorBlocks, currentTick, currentDifficulty);

                double prob = PortalLightProbability.compute(world, interiorBlocks, currentDifficulty);
                entry.accumulate(prob);
                entry.updateSubChunks(interiorBlocks,
                        getFlammableNeighbors(world, interiorBlocks),
                        getReachableLava(world, interiorBlocks));

                if (entry.isReadyToLight()) {
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
                        entry.fireScheduler.clearPreExistingFires(world, interiorBlocks);
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

    private boolean isPortalLit(ServerWorld world, List<BlockPos> interiorBlocks) {
        for (BlockPos pos : interiorBlocks) {
            if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.NETHER_PORTAL)) return true;
        }
        return false;
    }

}
