package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.vibzz.woodlightingstandards.mixin.FireBlockAccessor;

public class FireUtil {

    /**
     * Chance per tick that a given lava block runs onRandomTick. randomTickSpeed picks 3 of the
     * 4096 positions in a section, and lava is ticked twice per pick because FluidBlock is
     * registered ticksRandomly() and its randomTick delegates to the same fluid onRandomTick.
     */
    public static final double LAVA_TICK_CHANCE = 6.0 / 4096.0;

    public static final double AVG_FIRE_TICK_INTERVAL = 34.5;

    public static int getBurnChance(BlockState state) {
        if (isWaterlogged(state)) return 0;
        return ((FireBlockAccessor) Blocks.FIRE).getBurnChances().getInt(state.getBlock());
    }

    public static int getSpreadChance(BlockState state) {
        if (isWaterlogged(state)) return 0;
        return ((FireBlockAccessor) Blocks.FIRE).getSpreadChances().getInt(state.getBlock());
    }

    public static boolean isFlammable(BlockState state) {
        return getBurnChance(state) > 0;
    }

    public static boolean hasBurnableNeighbor(WorldAccess world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).getMaterial().isBurnable()) return true;
        }
        return false;
    }

    public static boolean isRainingAround(World world, BlockPos pos) {
        return world.hasRain(pos) || world.hasRain(pos.west()) || world.hasRain(pos.east())
                || world.hasRain(pos.north()) || world.hasRain(pos.south());
    }

    private static boolean isWaterlogged(BlockState state) {
        return state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
    }
}
