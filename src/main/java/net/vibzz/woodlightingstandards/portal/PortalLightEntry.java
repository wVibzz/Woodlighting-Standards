package net.vibzz.woodlightingstandards.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public final FireEventScheduler scheduler;
    public volatile double perTickProbability;
    public volatile boolean lit;
    public volatile boolean blocked;
    public final Map<BlockPos, Long> pendingExtinguish = new ConcurrentHashMap<>();
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
        this.scheduler = new FireEventScheduler(worldSeed, attempt, lowerCorner, axis, portalWidth);
    }

    public boolean matchesPortal(BlockPos otherCorner, Direction.Axis otherAxis) {
        return this.lowerCorner.equals(otherCorner) && this.axis == otherAxis;
    }
}
