package net.vibzz.woodlightingstandards.fire;

import net.minecraft.block.BlockState;
import net.vibzz.woodlightingstandards.util.FlammableBlockUtil;

/**
 * Computes deterministic burn-away times for flammable blocks near portals.
 * The actual burn time is derived from the world seed + block position,
 * falling within the min/max range for that block type.
 */
public class BurnAwayTiming {

    private static final int AVG_FIRE_TICK = 34;
    private static final int SPREAD_FACTOR = 300;

    public static int getMinTicks(BlockState state) {
        int spreadChance = FlammableBlockUtil.getSpreadChance(state);
        if (spreadChance <= 0) return -1;
        return (int) ((SPREAD_FACTOR / (double) spreadChance) * 0.3 * AVG_FIRE_TICK);
    }

    public static int getMaxTicks(BlockState state) {
        int spreadChance = FlammableBlockUtil.getSpreadChance(state);
        if (spreadChance <= 0) return -1;
        return (int) ((SPREAD_FACTOR / (double) spreadChance) * 2.5 * AVG_FIRE_TICK);
    }

    public static int calculateBurnTime(BlockState state, long worldSeed) {
        int min = getMinTicks(state);
        int max = getMaxTicks(state);
        if (min < 0 || max < 0) return -1;

        int spreadChance = FlammableBlockUtil.getSpreadChance(state);
        long hash = mixSeed(worldSeed ^ mixSeed(spreadChance));
        double uniform = (double) (hash & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;

        return min + (int) (uniform * (max - min));
    }

    private static long mixSeed(long seed) {
        seed ^= (seed >>> 30);
        seed *= 0xbf58476d1ce4e5b9L;
        seed ^= (seed >>> 27);
        seed *= 0x94d049bb133111ebL;
        seed ^= (seed >>> 31);
        return seed;
    }
}
