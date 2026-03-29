package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NetherPortalBlock.AreaHelper.class)
public interface AreaHelperAccessor {
    @Accessor("lowerCorner")
    BlockPos getLowerCorner();

    @Accessor("axis")
    Direction.Axis getAxis();
}
