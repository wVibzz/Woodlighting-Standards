package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.state.property.Properties;
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

        for (int offsetX = -3; offsetX <= 3; offsetX++) {
            for (int offsetY = -3; offsetY <= 0; offsetY++) {
                for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                    BlockPos lavaPos = airPos.add(offsetX, offsetY, offsetZ);
                    if (!world.getFluidState(lavaPos).isIn(FluidTags.LAVA)) continue;
                    if (!LavaReachUtil.canLavaReachSlot(world, lavaPos, airPos)) continue;

                    double fireReachProb = LavaWeightUtil.calculateWeight(
                            world, lavaPos, airPos, hasBurnableNeighbor);
                    prob += LAVA_TICK_CHANCE * fireReachProb;
                }
            }
        }

        return prob;
    }

    private static double computeFireSpreadContribution(ServerWorld world, BlockPos airPos, int difficulty, Set<BlockPos> excludeFires) {
        int maxBurnChance = 0;
        for (Direction dir : Direction.values()) {
            int burnChance = FlammableBlockUtil.getBurnChance(world.getBlockState(airPos.offset(dir)));
            if (burnChance > maxBurnChance) maxBurnChance = burnChance;
        }
        if (maxBurnChance == 0) return 0;

        if (world.isRaining() && isRainingAround(world, airPos)) return 0;

        double prob = 0;

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                for (int offsetY = -4; offsetY <= 1; offsetY++) {
                    if (offsetX == 0 && offsetY == 0 && offsetZ == 0) continue;

                    BlockPos firePos = airPos.add(offsetX, offsetY, offsetZ);
                    BlockState fireState = world.getBlockState(firePos);
                    if (!fireState.isIn(BlockTags.FIRE)) continue;
                    if (!excludeFires.isEmpty() && excludeFires.contains(firePos)) continue;

                    int targetOffsetFromFire = -offsetY;
                    int spreadResistance = 100;
                    if (targetOffsetFromFire > 1) spreadResistance += (targetOffsetFromFire - 1) * 100;

                    int fireAge = fireState.contains(Properties.AGE_15) ? fireState.get(Properties.AGE_15) : 0;
                    int igniteChance = (maxBurnChance + 40 + difficulty * 7) / (fireAge + 30);
                    if (world.hasHighHumidity(firePos)) igniteChance /= 2;
                    if (igniteChance <= 0) continue;

                    double perFireTick = Math.min(1.0, (igniteChance + 1.0) / spreadResistance);
                    prob += perFireTick / AVG_FIRE_TICK_INTERVAL;
                }
            }
        }

        return prob;
    }

    private static boolean isRainingAround(ServerWorld world, BlockPos pos) {
        return world.hasRain(pos) || world.hasRain(pos.west()) || world.hasRain(pos.east())
                || world.hasRain(pos.north()) || world.hasRain(pos.south());
    }

    private static boolean hasLavaBurnableNeighbor(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).getMaterial().isBurnable()) return true;
        }
        return false;
    }
}
