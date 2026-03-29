package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.server.world.ServerWorld;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void woodlight_tick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        WoodlightTracker.getInstance().tick((ServerWorld) (Object) this);
    }
}
