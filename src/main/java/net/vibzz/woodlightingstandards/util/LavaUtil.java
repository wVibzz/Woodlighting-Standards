package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

// Replicates vanilla LavaFluid.onRandomTick, (How often lava ignites one specific air block).
public class LavaUtil {

    private static final double BRANCH_CHANCE = 1.0 / 3;
    private static final double STEP_HITS_TARGET = 1.0 / 9;

    public static boolean canReach(WorldAccess world, BlockPos lavaPos, BlockPos airPos) {
        int deltaX = airPos.getX() - lavaPos.getX();
        int deltaY = airPos.getY() - lavaPos.getY();
        int deltaZ = airPos.getZ() - lavaPos.getZ();

        if (deltaY == 1 && within(deltaX, 1) && within(deltaZ, 1)) return true;

        if (deltaY == 2 && within(deltaX, 2) && within(deltaZ, 2)) {
            for (int firstStepX = -1; firstStepX <= 1; firstStepX++) {
                for (int firstStepZ = -1; firstStepZ <= 1; firstStepZ++) {
                    if (!within(deltaX - firstStepX, 1) || !within(deltaZ - firstStepZ, 1)) continue;
                    BlockPos firstStep = lavaPos.add(firstStepX, 1, firstStepZ);
                    if (!world.getBlockState(firstStep).getMaterial().blocksMovement()) return true;
                }
            }
        }

        return false;
    }

    public static double ignitionChance(WorldAccess world, BlockPos lavaPos, BlockPos airPos,
                                        boolean targetHasBurnableNeighbor) {
        int deltaX = airPos.getX() - lavaPos.getX();
        int deltaY = airPos.getY() - lavaPos.getY();
        int deltaZ = airPos.getZ() - lavaPos.getZ();

        return groundScanChance(world, lavaPos, deltaX, deltaY, deltaZ)
                + oneStepChance(deltaX, deltaY, deltaZ, targetHasBurnableNeighbor)
                + twoStepChance(world, lavaPos, deltaX, deltaY, deltaZ, targetHasBurnableNeighbor);
    }

    private static double groundScanChance(WorldAccess world, BlockPos lavaPos, int deltaX, int deltaY, int deltaZ) {
        if (deltaY != 1 || !within(deltaX, 1) || !within(deltaZ, 1)) return 0;

        BlockPos support = lavaPos.add(deltaX, 0, deltaZ);
        if (!world.getBlockState(support).getMaterial().isBurnable()) return 0;

        double anyOfThreeTries = 1.0 - Math.pow(1 - STEP_HITS_TARGET, 3);
        return BRANCH_CHANCE * anyOfThreeTries;
    }

    private static double oneStepChance(int deltaX, int deltaY, int deltaZ, boolean targetHasBurnableNeighbor) {
        if (!targetHasBurnableNeighbor) return 0;
        if (deltaY != 1 || !within(deltaX, 1) || !within(deltaZ, 1)) return 0;

        return (2 * BRANCH_CHANCE) * STEP_HITS_TARGET;
    }

    private static double twoStepChance(WorldAccess world, BlockPos lavaPos,
                                             int deltaX, int deltaY, int deltaZ, boolean targetHasBurnableNeighbor) {
        if (!targetHasBurnableNeighbor) return 0;
        if (deltaY != 2 || !within(deltaX, 2) || !within(deltaZ, 2)) return 0;

        double survivingPaths = 0;
        for (int firstStepX = -1; firstStepX <= 1; firstStepX++) {
            for (int firstStepZ = -1; firstStepZ <= 1; firstStepZ++) {
                if (!within(deltaX - firstStepX, 1) || !within(deltaZ - firstStepZ, 1)) continue;

                BlockPos firstStep = lavaPos.add(firstStepX, 1, firstStepZ);
                BlockState firstStepState = world.getBlockState(firstStep);

                if (firstStepState.getMaterial().blocksMovement()) continue;
                // lava would ignite here instead and stop walking
                if (firstStepState.isAir() && FireUtil.hasBurnableNeighbor(world, firstStep)) continue;

                survivingPaths += STEP_HITS_TARGET * STEP_HITS_TARGET;
            }
        }

        return BRANCH_CHANCE * survivingPaths;
    }

    private static boolean within(int delta, int max) {
        return delta >= -max && delta <= max;
    }
}
