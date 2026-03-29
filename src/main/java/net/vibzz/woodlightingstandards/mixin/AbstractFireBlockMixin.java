package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFireBlock.class)
public class AbstractFireBlockMixin {

    @Redirect(
        method = "onBlockAdded",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/NetherPortalBlock;createPortalAt(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;)Z")
    )
    private boolean redirectPortalCreation(WorldAccess worldAccess, BlockPos pos) {
        if (worldAccess instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) worldAccess;
            if (WoodlightTracker.getInstance().hasActiveTimer(serverWorld, pos)) {
                return false;
            }
        }
        return NetherPortalBlock.createPortalAt(worldAccess, pos);
    }

    /**
     * After fire is placed, if it's inside a tracked portal frame, remove it.
     * Prevents fire from visually sitting inside the portal doing nothing.
     */
    @Inject(method = "onBlockAdded", at = @At("TAIL"))
    private void removeFireInsideTrackedPortal(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
        if (world.isClient()) return;
        if (oldState.isOf(state.getBlock())) return;

        ServerWorld serverWorld = (ServerWorld) world;
        if (WoodlightTracker.getInstance().hasActiveTimer(serverWorld, pos)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }
}
