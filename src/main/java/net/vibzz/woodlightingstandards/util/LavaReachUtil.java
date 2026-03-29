package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

/**
 * Replicates vanilla LavaFluid.onRandomTick walk logic to determine
 * if a lava source can place fire that reaches a burn slot.
 */
public class LavaReachUtil {

    public static boolean canLavaReachSlot(WorldAccess world, BlockPos lavaPos, BlockPos slotPos) {
        // Branch 2 (i=0): fire at (+-1 X, +1 Y, +-1 Z) from lava
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos checkPos = lavaPos.add(dx, 0, dz);
                BlockPos firePos = checkPos.up();
                if (checkPos.equals(slotPos)) return true;
                if (isAdjacent(firePos, slotPos)) return true;
            }
        }

        // Branch 1 (i=1): one step (+-1 X, +1 Y, +-1 Z)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos step1 = lavaPos.add(dx, 1, dz);
                BlockState state1 = world.getBlockState(step1);

                if (state1.isAir() && isAdjacent(step1, slotPos)) return true;
            }
        }

        // Branch 1 (i=2): two steps
        for (int dx1 = -1; dx1 <= 1; dx1++) {
            for (int dz1 = -1; dz1 <= 1; dz1++) {
                BlockPos step1 = lavaPos.add(dx1, 1, dz1);
                BlockState state1 = world.getBlockState(step1);

                if (state1.getMaterial().blocksMovement()) continue;

                for (int dx2 = -1; dx2 <= 1; dx2++) {
                    for (int dz2 = -1; dz2 <= 1; dz2++) {
                        BlockPos step2 = step1.add(dx2, 1, dz2);
                        BlockState state2 = world.getBlockState(step2);

                        if (state2.isAir() && isAdjacent(step2, slotPos)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return (dx + dy + dz) == 1;
    }
}
