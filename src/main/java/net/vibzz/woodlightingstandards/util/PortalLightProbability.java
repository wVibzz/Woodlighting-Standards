package net.vibzz.woodlightingstandards.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Computes the per-tick probability of a portal lighting from lava fire generation,
 * based on the exact setup geometry and vanilla mechanics.
 */
public class PortalLightProbability {

    // randomTickSpeed(3) / 4096, doubled for the vanilla lava double-tick bug
    private static final double LAVA_TICK_CHANCE = 6.0 / 4096.0;

    public static double compute(ServerWorld world, List<BlockPos> interiorBlocks, int difficulty) {
        double totalProb = 0;

        for (BlockPos airPos : interiorBlocks) {
            int maxBurn = 0;
            for (Direction dir : Direction.values()) {
                int burn = FlammableBlockUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
                if (burn > maxBurn) maxBurn = burn;
            }
            if (maxBurn == 0) continue;

            // Vanilla: q = (burnChance + 40 + difficulty * 7) / (age + 30), then nextInt(100) <= q
            double q = (maxBurn + 40.0 + difficulty * 7) / 30.0;
            double igniteChance = Math.min(1.0, (q + 1) / 100.0);

            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 0; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos checkPos = airPos.add(dx, dy, dz);
                        if (!world.getFluidState(checkPos).isIn(FluidTags.LAVA)) continue;
                        if (!LavaReachUtil.canLavaReachSlot(world, checkPos, airPos)) continue;

                        double fireReachProb = LavaWeightUtil.calculateWeight(checkPos, airPos);
                        totalProb += LAVA_TICK_CHANCE * fireReachProb * igniteChance;
                    }
                }
            }
        }

        return Math.min(1.0, totalProb);
    }

}
