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
    private void suppressFireSpreadInTrackedArea(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        WoodlightTracker tracker = WoodlightTracker.getInstance();
        if (tracker.isEnabled(world) && tracker.isInTrackedArea(world, pos)) {
            world.getBlockTickScheduler().schedule(pos, state.getBlock(), 30 + random.nextInt(10));
            ci.cancel();
        }
    }
}