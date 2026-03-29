package net.vibzz.woodlightingstandards.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.vibzz.woodlightingstandards.client.WoodlightingstandardsClient;
import net.vibzz.woodlightingstandards.client.debug.WoodlightDebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void woodlight_renderHud(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (WoodlightingstandardsClient.isDebugVisible()) {
            WoodlightDebugHud.render(matrices, WoodlightingstandardsClient.SCAN_RESULT);
        }
    }
}
