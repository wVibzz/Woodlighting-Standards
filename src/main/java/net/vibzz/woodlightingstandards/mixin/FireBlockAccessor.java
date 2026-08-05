package net.vibzz.woodlightingstandards.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireBlock.class)
public interface FireBlockAccessor {
    @Accessor("burnChances")
    Object2IntMap<Block> getBurnChances();

    @Accessor("spreadChances")
    Object2IntMap<Block> getSpreadChances();
}
