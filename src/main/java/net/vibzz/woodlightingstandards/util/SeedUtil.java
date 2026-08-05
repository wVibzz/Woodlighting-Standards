package net.vibzz.woodlightingstandards.util;

public class SeedUtil {

    public static long mixSeed(long seed) {
        seed ^= (seed >>> 30);
        seed *= 0xbf58476d1ce4e5b9L;
        seed ^= (seed >>> 27);
        seed *= 0x94d049bb133111ebL;
        seed ^= (seed >>> 31);
        return seed;
    }

    public static double toUniform(long hash) {
        return (double) (hash & 0x7FFFFFFFFFFFFFFFL) / ((double) Long.MAX_VALUE + 1.0);
    }

    public static double calculateTargetCumulative(long seed, int attempt) {
        long mixed = mixSeed(seed ^ mixSeed(attempt));
        return -Math.log(1.0 - toUniform(mixed));
    }
}
