package net.vibzz.woodlightingstandards.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.vibzz.woodlightingstandards.client.WoodlightingstandardsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void woodlight_tick(CallbackInfo ci) {
        WoodlightingstandardsClient.onClientTick((MinecraftClient) (Object) this);
    }
}
