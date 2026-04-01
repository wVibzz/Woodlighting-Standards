package net.vibzz.woodlightingstandards.util;

import net.vibzz.woodlightingstandards.WoodlightConfig;

/**
 * Derives a deterministic woodlight delay from the world seed and
 * the vanilla-computed per-tick lighting probability.
 */
public class SeedTimingUtil {
    private static final double FALLBACK_LAMBDA = 0.06;

    public static int calculateTicks(long seed, int attempt, double perTickProbability) {
        long mixed = mixSeed(seed ^ mixSeed(attempt));
        double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;

        double ticks;
        if (perTickProbability > 0) {
            ticks = -Math.log(1.0 - uniform) / perTickProbability;
        } else {
            ticks = -Math.log(1.0 - uniform) / FALLBACK_LAMBDA * 20;
        }

        return Math.max(WoodlightConfig.MIN_SECONDS * 20, Math.min((int) ticks, WoodlightConfig.MAX_SECONDS * 20));
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
