package net.vibzz.woodlightingstandards.util;

public class SeedTimingUtil {
    private static final double LAMBDA = 0.06;
    private static final int MAX_TICKS = 75 * 20; // 75 seconds
    private static final int MIN_TICKS = 3 * 20;  // 3 seconds

    private static final double MAX_SETUP_REDUCTION = 0.40;
    private static final double MAX_LAVA_REDUCTION = 0.20;

    public static int calculateTicks(long seed, int attempt, int difficulty,
                                     double setupScore, double lavaScore) {
        if (difficulty <= 0) return MAX_TICKS;

        long mixed = mixSeed(seed ^ mixSeed(attempt));
        double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;
        double seconds = -Math.log(1.0 - uniform) / LAMBDA;

        // Vanilla: (burnChance + 40 + difficulty * 7) / (age + 30), ratio to normal
        double normalRate = (5.0 + 40 + 2 * 7) / 30.0;
        double diffRate = (5.0 + 40 + difficulty * 7) / 30.0;
        double diffMult = normalRate / diffRate;
        seconds *= diffMult;

        double setupMult = 1.0 - (Math.max(0, Math.min(1, setupScore)) * MAX_SETUP_REDUCTION);
        seconds *= setupMult;

        double lavaMult = 1.0 - (Math.max(0, Math.min(1, lavaScore)) * MAX_LAVA_REDUCTION);
        seconds *= lavaMult;

        int ticks = (int) (seconds * 20);
        return Math.max(MIN_TICKS, Math.min(ticks, MAX_TICKS));
    }

    /**
     * In vanilla, getBurnChanceAtAirPos returns the MAX burnChance of all 6 neighbors.
     * Multiple flammable blocks on the same air block don't stack - only the highest counts.
     * What matters is how many air blocks are covered and with what burnChance.
     *
     * @param airBlockBurnChances max burnChance adjacent to each portal interior air block (0 if uncovered)
     */
    public static double calculateSetupScore(int[] airBlockBurnChances) {
        if (airBlockBurnChances.length == 0) return 0.0;

        int coveredCount = 0;
        double weightedSum = 0;

        for (int burnChance : airBlockBurnChances) {
            if (burnChance > 0) {
                coveredCount++;
                weightedSum += burnChance / 5.0;
            }
        }

        if (coveredCount == 0) return 0.0;

        double coverageRatio = (double) coveredCount / airBlockBurnChances.length;
        double qualityRatio = weightedSum / airBlockBurnChances.length;

        return Math.min(1.0, coverageRatio * 0.5 + qualityRatio / 12.0 * 0.5);
    }

    /**
     * Lava score based on weighted effectiveness of each lava block.
     * Each lava block's weight reflects its actual vanilla probability contribution.
     */
    public static double calculateLavaScore(double totalLavaWeight) {
        if (totalLavaWeight <= 0) return 0.0;
        double raw = Math.log(1 + totalLavaWeight) / Math.log(1 + 12);
        return Math.min(1.0, raw);
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
