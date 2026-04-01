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
        int dx = airPos.getX() - lavaPos.getX();
        int dy = airPos.getY() - lavaPos.getY();
        int dz = airPos.getZ() - lavaPos.getZ();

        double weight = 0;
        weight += branch2Weight(world, lavaPos, dx, dy, dz);
        weight += branch1Step1Weight(dx, dy, dz, airHasBurnableNeighbor);
        weight += branch1Step2Weight(world, lavaPos, dx, dy, dz, airHasBurnableNeighbor);
        return weight;
    }

    // i=0 (1/3 chance), 3 attempts
    // blockPos2 = lava.add(randDx, 0, randDz), fire at blockPos2.up()
    // Vanilla checks hasBurnableBlock(blockPos2) = Material.isBurnable() at blockPos2
    private static double branch2Weight(WorldAccess world, BlockPos lavaPos, int dx, int dy, int dz) {
        if (dy != 1) return 0;
        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return 0;

        // blockPos2 = lava.add(dx, 0, dz) — check if THAT block is burnable
        BlockPos blockPos2 = lavaPos.add(dx, 0, dz);
        if (!world.getBlockState(blockPos2).getMaterial().isBurnable()) return 0;

        double pHit = 1.0 - Math.pow(8.0 / 9, 3);
        return (1.0 / 3) * pHit;
    }

    // i=1 or i=2 first step (2/3 chance), step to (+-1X, +1Y, +-1Z)
    // Both i=1 and i=2 take a first step at +1Y. If it lands on the target
    // and canLightFire passes, fire is placed and the method returns.
    private static double branch1Step1Weight(int dx, int dy, int dz, boolean airHasBurnableNeighbor) {
        if (!airHasBurnableNeighbor) return 0;
        if (dy != 1) return 0;
        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return 0;
        return (2.0 / 3) * (1.0 / 9);
    }

    // i=2 (1/3 chance), two steps each (+-1X, +1Y, +-1Z)
    // Step 1 can place fire and return early, or hit solid and return.
    private static double branch1Step2Weight(WorldAccess world, BlockPos lavaPos,
                                             int dx, int dy, int dz, boolean airHasBurnableNeighbor) {
        if (!airHasBurnableNeighbor) return 0;
        if (dy != 2) return 0;
        if (dx < -2 || dx > 2 || dz < -2 || dz > 2) return 0;

        double prob = 0;
        for (int s1dx = -1; s1dx <= 1; s1dx++) {
            for (int s1dz = -1; s1dz <= 1; s1dz++) {
                int s2dx = dx - s1dx;
                int s2dz = dz - s1dz;
                if (s2dx < -1 || s2dx > 1 || s2dz < -1 || s2dz > 1) continue;

                BlockPos step1Pos = lavaPos.add(s1dx, 1, s1dz);
                BlockState step1State = world.getBlockState(step1Pos);

                if (step1State.getMaterial().blocksMovement()) continue;

                if (step1State.isAir() && canLightFire(world, step1Pos)) continue;

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
