package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(FireBlock.class)
public class FireBlockMixin {

    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void suppressFireSpreadInPortalSubChunk(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        WoodlightTracker tracker = WoodlightTracker.getInstance();
        if (tracker.isEnabled(world) && tracker.isPortalSubChunk(world, pos)) {
            ci.cancel();
        }
    }
}
