package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

/**
 * Checks if a lava source can place fire at the exact position of a
 * portal air block, replicating vanilla LavaFluid.onRandomTick walk logic.
 */
public class LavaReachUtil {

    public static boolean canLavaReachSlot(WorldAccess world, BlockPos lavaPos, BlockPos airPos) {
        int dx = airPos.getX() - lavaPos.getX();
        int dy = airPos.getY() - lavaPos.getY();
        int dz = airPos.getZ() - lavaPos.getZ();

        // Branch 2 and branch 1 (i=1) both reach (+-1X, +1Y, +-1Z)
        if (dy == 1 && dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
            return true;
        }

        // Branch 1 (i=2): two steps, fire at (+-2X, +2Y, +-2Z) from lava
        if (dy == 2 && dx >= -2 && dx <= 2 && dz >= -2 && dz <= 2) {
            // Check if any valid step1 path exists (not blocked)
            for (int s1dx = -1; s1dx <= 1; s1dx++) {
                for (int s1dz = -1; s1dz <= 1; s1dz++) {
                    int s2dx = dx - s1dx;
                    int s2dz = dz - s1dz;
                    if (s2dx < -1 || s2dx > 1 || s2dz < -1 || s2dz > 1) continue;

                    BlockPos step1 = lavaPos.add(s1dx, 1, s1dz);
                    BlockState state1 = world.getBlockState(step1);
                    if (state1.getMaterial().blocksMovement()) continue;

                    // Step 1 passable, step 2 can reach target
                    return true;
                }
            }
        }

        return false;
    }
}
