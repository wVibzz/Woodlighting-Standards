package net.vibzz.woodlightingstandards.fire;

import net.minecraft.block.BlockState;import net.vibzz.woodlightingstandards.util.FireUtil;
import net.vibzz.woodlightingstandards.util.SeedUtil;

/**
 * Computes deterministic burn-away times for flammable blocks near portals.
 * The burn time is derived from the world seed and a portal-relative position
 * key, falling within the min/max range for that block type.
 */
public class BurnAwayTiming {

    private static final int AVG_FIRE_TICK = 34;
    private static final int SPREAD_FACTOR = 300;

    public static int getMinTicks(BlockState state) {
        int spreadChance = FireUtil.getSpreadChance(state);
        if (spreadChance <= 0) return -1;
        return (int) ((SPREAD_FACTOR / (double) spreadChance) * 0.3 * AVG_FIRE_TICK);
    }

    public static int getMaxTicks(BlockState state) {
        int spreadChance = FireUtil.getSpreadChance(state);
        if (spreadChance <= 0) return -1;
        return (int) ((SPREAD_FACTOR / (double) spreadChance) * 2.5 * AVG_FIRE_TICK);
    }

    public static int calculateBurnTime(BlockState state, long positionKey, long worldSeed) {
        int min = getMinTicks(state);
        int max = getMaxTicks(state);
        if (min < 0 || max < 0) return -1;

        int spreadChance = FireUtil.getSpreadChance(state);
        long hash = SeedUtil.mixSeed(worldSeed ^ SeedUtil.mixSeed(spreadChance) ^ SeedUtil.mixSeed(positionKey));

        return min + (int) (SeedUtil.toUniform(hash) * (max - min));
    }
}
