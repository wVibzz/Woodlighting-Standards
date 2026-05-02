package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.vibzz.woodlightingstandards.fire.FireEventScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractFireBlock.class)
public class AbstractFireBlockMixin {

    @Redirect(
        method = "onBlockAdded",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/NetherPortalBlock;createPortalAt(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;)Z")
    )
    private boolean redirectPortalCreation(WorldAccess worldAccess, BlockPos pos) {
        if (FireEventScheduler.isSuppressingFirePortal()) {
            return false;
        }
        return NetherPortalBlock.createPortalAt(worldAccess, pos);
    }
}
