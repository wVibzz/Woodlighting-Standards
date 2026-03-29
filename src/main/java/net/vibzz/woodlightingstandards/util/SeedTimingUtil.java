package net.vibzz.woodlightingstandards.util;

public class SeedTimingUtil {
    private static final double LAMBDA = 0.06;
    private static final int MAX_TICKS = 75 * 20; // 75 seconds
    private static final int MIN_TICKS = 3 * 20;  // 3 seconds

    private static final double[] DIFFICULTY_MULTIPLIERS = {
        0.0,  // Peaceful (disabled)
        1.3,  // Easy
        1.0,  // Normal
        0.8   // Hard
    };

    private static final double MAX_SETUP_REDUCTION = 0.40;
    private static final double MAX_LAVA_REDUCTION = 0.20;

    public static int calculateTicks(long seed, int attempt, int difficulty,
                                     double setupScore, double lavaScore) {
        if (difficulty <= 0) return MAX_TICKS;

        long mixed = mixSeed(seed ^ mixSeed(attempt));
        double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;
        double seconds = -Math.log(1.0 - uniform) / LAMBDA;

        double diffMult = (difficulty < DIFFICULTY_MULTIPLIERS.length)
                ? DIFFICULTY_MULTIPLIERS[difficulty] : 1.0;
        seconds *= diffMult;

        double setupMult = 1.0 - (Math.max(0, Math.min(1, setupScore)) * MAX_SETUP_REDUCTION);
        seconds *= setupMult;

        double lavaMult = 1.0 - (Math.max(0, Math.min(1, lavaScore)) * MAX_LAVA_REDUCTION);
        seconds *= lavaMult;

        int ticks = (int) (seconds * 20);
        return Math.max(MIN_TICKS, Math.min(ticks, MAX_TICKS));
    }


    /**
     * Score based on total spreadChance of filled burn slots,
     * normalized against all slots filled with leaves/wool (spreadChance 60).
     * Subject to change.
     */
    public static double calculateSetupScore(int[] burnSlotSpreadChances, int maxBurnSlots) {
        if (maxBurnSlots <= 0) return 0.0;

        int totalSpread = 0;
        for (int spread : burnSlotSpreadChances) {
            totalSpread += spread;
        }

        int maxTotal = maxBurnSlots * 60;
        return Math.min(1.0, (double) totalSpread / maxTotal);
    }

    /**
     * Score based on reachable lava count near filled burn slots.
     * Logarithmic scaling: 1 lava ~30%, 4 ~67%, 8 ~90%, 12+ ~100%.
     * Subject to change.
     */
    public static double calculateLavaScore(int effectiveLavaCount) {
        if (effectiveLavaCount <= 0) return 0.0;
        double raw = Math.log(1 + effectiveLavaCount) / Math.log(1 + 12);
        return Math.min(1.0, raw);
    }

    // Stafford variant 13 of MurmurHash3 finalizer
    private static long mixSeed(long seed) {
        seed ^= (seed >>> 30);
        seed *= 0xbf58476d1ce4e5b9L;
        seed ^= (seed >>> 27);
        seed *= 0x94d049bb133111ebL;
        seed ^= (seed >>> 31);
        return seed;
    }
}
