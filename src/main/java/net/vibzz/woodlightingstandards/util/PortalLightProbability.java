package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PortalLightProbability {

    private static final double LAVA_TICK_CHANCE = 6.0 / 4096.0;
    private static final double AVG_FIRE_TICK_INTERVAL = 34.5;

    public static double compute(ServerWorld world, List<BlockPos> interiorBlocks, int difficulty) {
        return compute(world, interiorBlocks, difficulty, Collections.emptySet());
    }

    public static double compute(ServerWorld world, List<BlockPos> interiorBlocks, int difficulty, Set<BlockPos> excludeFires) {
        double survivalProb = 1.0;

        for (BlockPos airPos : interiorBlocks) {
            double airBlockProb = 0;
            airBlockProb += computeLavaContribution(world, airPos);
            airBlockProb += computeFireSpreadContribution(world, airPos, difficulty, excludeFires);

            if (airBlockProb > 0) {
                survivalProb *= (1.0 - Math.min(1.0, airBlockProb));
            }
        }

        return 1.0 - survivalProb;
    }

    private static double computeLavaContribution(ServerWorld world, BlockPos airPos) {
        boolean hasBurnableNeighbor = hasLavaBurnableNeighbor(world, airPos);
        double prob = 0;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 0; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = airPos.add(dx, dy, dz);
                    if (!world.getFluidState(checkPos).isIn(FluidTags.LAVA)) continue;
                    if (!LavaReachUtil.canLavaReachSlot(world, checkPos, airPos)) continue;

                    double fireReachProb = LavaWeightUtil.calculateWeight(
                            world, checkPos, airPos, hasBurnableNeighbor);
                    prob += LAVA_TICK_CHANCE * fireReachProb;
                }
            }
        }

        return prob;
    }

    private static double computeFireSpreadContribution(ServerWorld world, BlockPos airPos, int difficulty, Set<BlockPos> excludeFires) {
        int maxBurn = 0;
        for (Direction dir : Direction.values()) {
            int burn = FlammableBlockUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
            if (burn > maxBurn) maxBurn = burn;
        }
        if (maxBurn == 0) return 0;

        double prob = 0;

        for (int l = -1; l <= 1; l++) {
            for (int m = -1; m <= 1; m++) {
                for (int n = -1; n <= 4; n++) {
                    if (l == 0 && n == 0 && m == 0) continue;

                    BlockPos firePos = airPos.add(l, n, m);
                    BlockState fireState = world.getBlockState(firePos);
                    if (!fireState.isIn(BlockTags.FIRE)) continue;
                    if (!excludeFires.isEmpty() && excludeFires.contains(firePos)) continue;

                    int o = 100;
                    if (n > 1) o += (n - 1) * 100;

                    double q = (maxBurn + 40.0 + difficulty * 7) / 30.0;
                    double perFireTick = Math.min(1.0, (q + 1) / o);
                    prob += perFireTick / AVG_FIRE_TICK_INTERVAL;
                }
            }
        }

        return prob;
    }

    private static boolean hasLavaBurnableNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).getMaterial().isBurnable()) return true;
        }
        return false;
    }
}
