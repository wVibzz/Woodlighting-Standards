package net.vibzz.woodlightingstandards.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import net.vibzz.woodlightingstandards.client.WoodlightingstandardsClient;
import net.vibzz.woodlightingstandards.client.debug.WoodlightDebugOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void woodlight_renderOverlay(
            MatrixStack matrices, float tickDelta, long limitTime,
            boolean renderBlockOutline, Camera camera,
            GameRenderer gameRenderer, LightmapTextureManager lightmap,
            Matrix4f projection, CallbackInfo ci) {

        if (WoodlightingstandardsClient.isDebugVisible()) {
            WoodlightDebugOverlay.render(matrices, camera, WoodlightingstandardsClient.SCAN_RESULT);
        }
    }
}
