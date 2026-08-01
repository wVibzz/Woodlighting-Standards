package net.vibzz.woodlightingstandards.mixin;

import net.minecraft.server.MinecraftServer;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void woodlight_shutdown(CallbackInfo ci) {
        WoodlightTracker.getInstance().reset();
    }
}
