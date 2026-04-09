package net.vibzz.woodlightingstandards.util;

/**
 * Derives a deterministic target cumulative probability from the world seed.
 * The portal lights once the actual cumulative probability reaches this target.
 */
public class SeedTimingUtil {

    public static double calculateTargetCumulative(long seed, int attempt) {
        long mixed = mixSeed(seed ^ mixSeed(attempt));
        double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;
        return -Math.log(1.0 - uniform);
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
