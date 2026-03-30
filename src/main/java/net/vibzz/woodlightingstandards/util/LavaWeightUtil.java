package net.vibzz.woodlightingstandards.util;

import net.minecraft.util.math.BlockPos;

/**
 * Calculates the per-tick probability weight of a lava block contributing
 * to lighting a portal, based on vanilla LavaFluid.onRandomTick mechanics.

 * Vanilla has two branches per random tick:
 *   Branch 1 (probability 2/3): walk 1-2 steps, each (+-1X, +1Y, +-1Z)
 *   Branch 2 (probability 1/3): check 3 random positions at (+-1X, 0Y, +-1Z), fire at +1Y

 * Each lava block gets randomTickSpeed (3) chances per tick to be selected
 * from its 4096-block subchunk, doubled due to the vanilla bug where lava
 * gets ticked in both blockstate and fluidstate random ticks.
 */
public class LavaWeightUtil {

    /**
     * Calculate the relative weight of a lava block at lavaPos for lighting
     * the portal air block at airPos. Higher weight = more effective.

     * Returns the probability per random tick attempt that this specific
     * lava block places fire adjacent to (or at) airPos.
     */
    public static double calculateWeight(BlockPos lavaPos, BlockPos airPos) {
        double weight = 0;

        int dx = airPos.getX() - lavaPos.getX();
        int dy = airPos.getY() - lavaPos.getY();
        int dz = airPos.getZ() - lavaPos.getZ();

        weight += branch2Weight(dx, dy, dz);
        weight += branch1Step1Weight(dx, dy, dz);
        weight += branch1Step2Weight(dx, dy, dz);

        return weight;
    }

    // i=0 (1/3 chance), 3 attempts at (+-1X, +1Y, +-1Z) from lava
    private static double branch2Weight(int dx, int dy, int dz) {
        double prob = 0;
        for (int fdx = -1; fdx <= 1; fdx++) {
            for (int fdz = -1; fdz <= 1; fdz++) {
                if (isAdjacentOrEqual(fdx, 1, fdz, dx, dy, dz)) {
                    prob += (1.0 / 3) * (1.0 / 9);
                }
            }
        }
        return prob * 3;
    }

    // i=1 (1/3 chance), one step (+-1X, +1Y, +-1Z)
    private static double branch1Step1Weight(int dx, int dy, int dz) {
        double prob = 0;
        for (int sdx = -1; sdx <= 1; sdx++) {
            for (int sdz = -1; sdz <= 1; sdz++) {
                if (isAdjacentOrEqual(sdx, 1, sdz, dx, dy, dz)) {
                    prob += (1.0 / 3) * (1.0 / 9);
                }
            }
        }
        return prob;
    }

    // i=2 (1/3 chance), two steps each (+-1X, +1Y, +-1Z)
    private static double branch1Step2Weight(int dx, int dy, int dz) {
        double prob = 0;
        for (int s1dx = -1; s1dx <= 1; s1dx++) {
            for (int s1dz = -1; s1dz <= 1; s1dz++) {
                for (int s2dx = -1; s2dx <= 1; s2dx++) {
                    for (int s2dz = -1; s2dz <= 1; s2dz++) {
                        if (isAdjacentOrEqual(s1dx + s2dx, 2, s1dz + s2dz, dx, dy, dz)) {
                            prob += (1.0 / 3) * (1.0 / 81);
                        }
                    }
                }
            }
        }
        return prob;
    }

    private static boolean isAdjacentOrEqual(int fx, int fy, int fz, int tx, int ty, int tz) {
        int ddx = Math.abs(fx - tx);
        int ddy = Math.abs(fy - ty);
        int ddz = Math.abs(fz - tz);
        return (ddx + ddy + ddz) <= 1;
    }
}
