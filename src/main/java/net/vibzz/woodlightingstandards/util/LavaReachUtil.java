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
        int deltaX = airPos.getX() - lavaPos.getX();
        int deltaY = airPos.getY() - lavaPos.getY();
        int deltaZ = airPos.getZ() - lavaPos.getZ();

        // Branch 2 and branch 1 (i=1) both reach (+-1X, +1Y, +-1Z)
        if (deltaY == 1 && deltaX >= -1 && deltaX <= 1 && deltaZ >= -1 && deltaZ <= 1) {
            return true;
        }

        // Branch 1 (i=2): two steps, fire at (+-2X, +2Y, +-2Z) from lava
        if (deltaY == 2 && deltaX >= -2 && deltaX <= 2 && deltaZ >= -2 && deltaZ <= 2) {
            // Check if any valid first-step path exists (not blocked)
            for (int firstStepX = -1; firstStepX <= 1; firstStepX++) {
                for (int firstStepZ = -1; firstStepZ <= 1; firstStepZ++) {
                    int secondStepX = deltaX - firstStepX;
                    int secondStepZ = deltaZ - firstStepZ;
                    if (secondStepX < -1 || secondStepX > 1 || secondStepZ < -1 || secondStepZ > 1) continue;

                    BlockPos firstStepPos = lavaPos.add(firstStepX, 1, firstStepZ);
                    BlockState firstStepState = world.getBlockState(firstStepPos);
                    if (firstStepState.getMaterial().blocksMovement()) continue;

                    // First step passable, second step can reach target
                    return true;
                }
            }
        }

        return false;
    }
}
