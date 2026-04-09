package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractFireBlock.class)
public class AbstractFireBlockMixin {

    /**
     * Redirect createPortalAt in onBlockAdded.
     * If fire appears inside a tracked portal frame, let vanilla light it
     * (this allows manual lighting via flint & steel or thrown fire).
     * Spread fire from outside the frame won't reach here because the scheduler
     * blocks fire placement inside the portal interior.
     */
    @Redirect(
        method = "onBlockAdded",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/NetherPortalBlock;createPortalAt(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;)Z")
    )
    private boolean redirectPortalCreation(WorldAccess worldAccess, BlockPos pos) {
        return NetherPortalBlock.createPortalAt(worldAccess, pos);
    }
}
