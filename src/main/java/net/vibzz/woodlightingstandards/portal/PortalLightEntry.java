package net.vibzz.woodlightingstandards.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.vibzz.woodlightingstandards.util.SeedTimingUtil;

public class PortalLightEntry {
    public final BlockPos lowerCorner;
    public final Direction.Axis axis;
    public final BlockPos probePos;
    public final int attempt;
    public final long startTick;
    public final long worldSeed;

    public int delayTicks;
    public long targetTick;
    public double perTickProbability;
    private double lastProbability = -1;

    public PortalLightEntry(BlockPos lowerCorner, Direction.Axis axis, BlockPos probePos,
                            int attempt, long startTick, long worldSeed, double perTickProbability) {
        this.lowerCorner = lowerCorner.toImmutable();
        this.axis = axis;
        this.probePos = probePos.toImmutable();
        this.attempt = attempt;
        this.startTick = startTick;
        this.worldSeed = worldSeed;
        this.perTickProbability = perTickProbability;
        recalculate(perTickProbability);
    }

    public void recalculate(double newProbability) {
        if (newProbability == lastProbability) return;
        lastProbability = newProbability;
        this.perTickProbability = newProbability;
        this.delayTicks = SeedTimingUtil.calculateTicks(worldSeed, attempt, newProbability);
        this.targetTick = startTick + delayTicks;
    }

    public boolean matchesPortal(BlockPos otherCorner, Direction.Axis otherAxis) {
        return this.lowerCorner.equals(otherCorner) && this.axis == otherAxis;
    }
}
