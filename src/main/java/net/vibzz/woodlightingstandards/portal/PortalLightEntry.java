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
    public double setupScore;
    public double lavaScore;
    private int lastDifficulty = -1;
    private double lastSetupScore = -1;
    private double lastLavaScore = -1;

    public PortalLightEntry(BlockPos lowerCorner, Direction.Axis axis, BlockPos probePos,
                            int attempt, long startTick, long worldSeed, int difficulty) {
        this.lowerCorner = lowerCorner.toImmutable();
        this.axis = axis;
        this.probePos = probePos.toImmutable();
        this.attempt = attempt;
        this.startTick = startTick;
        this.worldSeed = worldSeed;
        this.setupScore = 0.0;
        this.lavaScore = 0.0;
        recalculate(difficulty, 0.0, 0.0);
    }

    public void recalculate(int difficulty, double newSetupScore, double newLavaScore) {
        if (difficulty == lastDifficulty && newSetupScore == lastSetupScore && newLavaScore == lastLavaScore) return;
        lastDifficulty = difficulty;
        lastSetupScore = newSetupScore;
        lastLavaScore = newLavaScore;
        this.setupScore = newSetupScore;
        this.lavaScore = newLavaScore;
        this.delayTicks = SeedTimingUtil.calculateTicks(worldSeed, attempt, difficulty, newSetupScore, newLavaScore);
        this.targetTick = startTick + delayTicks;
    }

    public boolean matchesPortal(BlockPos otherCorner, Direction.Axis otherAxis) {
        return this.lowerCorner.equals(otherCorner) && this.axis == otherAxis;
    }
}
