package net.vibzz.woodlightingstandards.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.List;

/**
 * Computes the per-tick probability of a portal lighting from direct lava fire generation.
 * Does not include fire-to-fire spread (known limitation).
 */
public class PortalLightProbability {

    // randomTickSpeed(3) / 4096, doubled for the vanilla lava double-tick bug
    private static final double LAVA_TICK_CHANCE = 6.0 / 4096.0;

    public static double compute(ServerWorld world, List<BlockPos> interiorBlocks, int difficulty) {
        double survivalProb = 1.0;

        for (BlockPos airPos : interiorBlocks) {
            boolean hasBurnableNeighbor = hasLavaBurnableNeighbor(world, airPos);

            double airBlockProb = 0;

            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 0; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos checkPos = airPos.add(dx, dy, dz);
                        if (!world.getFluidState(checkPos).isIn(FluidTags.LAVA)) continue;
                        if (!LavaReachUtil.canLavaReachSlot(world, checkPos, airPos)) continue;

                        double fireReachProb = LavaWeightUtil.calculateWeight(
                                world, checkPos, airPos, hasBurnableNeighbor);
                        airBlockProb += LAVA_TICK_CHANCE * fireReachProb;
                    }
                }
            }

            if (airBlockProb > 0) {
                survivalProb *= (1.0 - Math.min(1.0, airBlockProb));
            }
        }

        return 1.0 - survivalProb;
    }

    private static boolean hasLavaBurnableNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).getMaterial().isBurnable()) return true;
        }
        return false;
    }
}
