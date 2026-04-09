package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(LavaFluid.class)
public class LavaFluidMixin {

    @Inject(method = "onRandomTick", at = @At("HEAD"), cancellable = true)
    private void suppressLavaFireInPortalSubChunk(World world, BlockPos pos, FluidState state, Random random, CallbackInfo ci) {
        if (world instanceof ServerWorld) {
            if (WoodlightTracker.getInstance().isPortalSubChunk((ServerWorld) world, pos)) {
                ci.cancel();
            }
        }
    }
}
