package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

/**
 * Calculates the probability that a lava block places fire at the exact
 * position of a portal air block, matching vanilla LavaFluid.onRandomTick.

 * Branch 1 (i=1 or i=2): walk steps (+-1X, +1Y, +-1Z), canLightFire on fire pos
 * Branch 2 (i=0): 3 attempts (+-1X, 0Y, +-1Z), hasBurnableBlock on THAT pos, fire at +1Y
 */
public class LavaWeightUtil {

    public static double calculateWeight(WorldAccess world, BlockPos lavaPos, BlockPos airPos,
                                         boolean airHasBurnableNeighbor) {
        int deltaX = airPos.getX() - lavaPos.getX();
        int deltaY = airPos.getY() - lavaPos.getY();
        int deltaZ = airPos.getZ() - lavaPos.getZ();

        double weight = 0;
        weight += branch2Weight(world, lavaPos, deltaX, deltaY, deltaZ);
        weight += branch1Step1Weight(deltaX, deltaY, deltaZ, airHasBurnableNeighbor);
        weight += branch1Step2Weight(world, lavaPos, deltaX, deltaY, deltaZ, airHasBurnableNeighbor);
        return weight;
    }

    // i=0 (1/3 chance), 3 attempts
    // blockPos2 = lava.add(randDx, 0, randDz), fire at blockPos2.up()
    // Vanilla checks hasBurnableBlock(blockPos2) = Material.isBurnable() at blockPos2
    private static double branch2Weight(WorldAccess world, BlockPos lavaPos, int deltaX, int deltaY, int deltaZ) {
        if (deltaY != 1) return 0;
        if (deltaX < -1 || deltaX > 1 || deltaZ < -1 || deltaZ > 1) return 0;

        BlockPos supportPos = lavaPos.add(deltaX, 0, deltaZ);
        if (!world.getBlockState(supportPos).getMaterial().isBurnable()) return 0;

        double hitChance = 1.0 - Math.pow(8.0 / 9, 3);
        return (1.0 / 3) * hitChance;
    }

    // i=1 or i=2 first step (2/3 chance), step to (+-1X, +1Y, +-1Z)
    // Both i=1 and i=2 take a first step at +1Y. If it lands on the target
    // and canLightFire passes, fire is placed and the method returns.
    private static double branch1Step1Weight(int deltaX, int deltaY, int deltaZ, boolean airHasBurnableNeighbor) {
        if (!airHasBurnableNeighbor) return 0;
        if (deltaY != 1) return 0;
        if (deltaX < -1 || deltaX > 1 || deltaZ < -1 || deltaZ > 1) return 0;
        return (2.0 / 3) * (1.0 / 9);
    }

    // i=2 (1/3 chance), two steps each (+-1X, +1Y, +-1Z)
    // Step 1 can place fire and return early, or hit solid and return.
    private static double branch1Step2Weight(WorldAccess world, BlockPos lavaPos,
                                             int deltaX, int deltaY, int deltaZ, boolean airHasBurnableNeighbor) {
        if (!airHasBurnableNeighbor) return 0;
        if (deltaY != 2) return 0;
        if (deltaX < -2 || deltaX > 2 || deltaZ < -2 || deltaZ > 2) return 0;

        double prob = 0;
        for (int firstStepX = -1; firstStepX <= 1; firstStepX++) {
            for (int firstStepZ = -1; firstStepZ <= 1; firstStepZ++) {
                int secondStepX = deltaX - firstStepX;
                int secondStepZ = deltaZ - firstStepZ;
                if (secondStepX < -1 || secondStepX > 1 || secondStepZ < -1 || secondStepZ > 1) continue;

                BlockPos firstStepPos = lavaPos.add(firstStepX, 1, firstStepZ);
                BlockState firstStepState = world.getBlockState(firstStepPos);

                if (firstStepState.getMaterial().blocksMovement()) continue;

                if (firstStepState.isAir() && canLightFire(world, firstStepPos)) continue;

                prob += 1.0 / 81.0;
            }
        }

        return (1.0 / 3) * prob;
    }

    private static boolean canLightFire(WorldAccess world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).getMaterial().isBurnable()) return true;
        }
        return false;
    }
}
