package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.vibzz.woodlightingstandards.mixin.FireBlockAccessor;

public class FlammableBlockUtil {

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

    private static boolean isWaterlogged(BlockState state) {
        return state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
    }
}
