package net.vibzz.woodlightingstandards.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.vibzz.woodlightingstandards.fire.FireEventScheduler;
import net.vibzz.woodlightingstandards.util.SeedTimingUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PortalLightEntry {
    public final BlockPos lowerCorner;
    public final Direction.Axis axis;
    public final BlockPos probePos;
    public final int attempt;
    public final long startTick;
    public final long worldSeed;
    public final int portalWidth;
    public final int portalHeight;
    public final Set<Long> portalSubChunks = ConcurrentHashMap.newKeySet();
    public final FireEventScheduler fireScheduler;

    public final double targetCumulative;
    public volatile double cumulativeProbability;
    public volatile double perTickProbability;
    public volatile boolean lit;

    public volatile List<BlockPos> cachedInterior = new ArrayList<>();
    public volatile List<BlockPos> cachedFrame = new ArrayList<>();
    public volatile List<BlockPos> cachedFlammable = new ArrayList<>();
    public volatile List<BlockPos> cachedLava = new ArrayList<>();
    public volatile int cachedFireCount;
    public volatile List<BlockPos> cachedFirePositions = new ArrayList<>();

    public PortalLightEntry(BlockPos lowerCorner, Direction.Axis axis, BlockPos probePos,
                            int attempt, long startTick, long worldSeed, double perTickProbability,
                            int portalWidth, int portalHeight) {
        this.lowerCorner = lowerCorner.toImmutable();
        this.axis = axis;
        this.probePos = probePos.toImmutable();
        this.attempt = attempt;
        this.startTick = startTick;
        this.worldSeed = worldSeed;
        this.portalWidth = portalWidth;
        this.portalHeight = portalHeight;
        this.perTickProbability = perTickProbability;
        this.fireScheduler = new FireEventScheduler(worldSeed, attempt, lowerCorner, axis, portalWidth);
        this.targetCumulative = SeedTimingUtil.calculateTargetCumulative(worldSeed, attempt);
        this.cumulativeProbability = 0;
    }

    public void accumulate(double probability) {
        this.perTickProbability = probability;
        if (probability > 0) {
            this.cumulativeProbability += probability;
        }
    }

    public boolean isReadyToLight() {
        return cumulativeProbability >= targetCumulative;
    }

    public void updateSubChunks(List<BlockPos> interiorBlocks, List<BlockPos> flammableBlocks, List<BlockPos> lavaBlocks) {
        portalSubChunks.clear();
        for (BlockPos pos : interiorBlocks) portalSubChunks.add(subChunkKey(pos));
        for (BlockPos pos : flammableBlocks) portalSubChunks.add(subChunkKey(pos));
        for (BlockPos pos : lavaBlocks) portalSubChunks.add(subChunkKey(pos));
    }

    private static long subChunkKey(BlockPos pos) {
        return ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public boolean matchesPortal(BlockPos otherCorner, Direction.Axis otherAxis) {
        return this.lowerCorner.equals(otherCorner) && this.axis == otherAxis;
    }
}
