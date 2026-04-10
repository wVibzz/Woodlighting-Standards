package net.vibzz.woodlightingstandards.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.vibzz.woodlightingstandards.WoodlightBuildConfig;
import net.vibzz.woodlightingstandards.Woodlightingstandards;
import net.vibzz.woodlightingstandards.client.debug.PortalScanResult;
import net.vibzz.woodlightingstandards.mixin.client.GameOptionsAccessor;
import org.lwjgl.glfw.GLFW;

public class WoodlightingstandardsClient implements ClientModInitializer {

    public static final PortalScanResult SCAN_RESULT = new PortalScanResult();

    private static KeyBinding scanKey;
    private static boolean debugVisible = false;
    private static boolean keysRegistered = false;

    @Override
    public void onInitializeClient() {
        if (!WoodlightBuildConfig.DEBUG) return;

        scanKey = new KeyBinding(
                "key.woodlightingstandards.scan",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "key.categories.misc"
        );
    }

    private static void ensureKeysRegistered() {
        if (keysRegistered) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        GameOptionsAccessor accessor = (GameOptionsAccessor) mc.options;
        KeyBinding[] oldKeys = accessor.getKeysAll();
        KeyBinding[] newKeys = new KeyBinding[oldKeys.length + 1];
        System.arraycopy(oldKeys, 0, newKeys, 0, oldKeys.length);
        newKeys[oldKeys.length] = scanKey;
        accessor.setKeysAll(newKeys);
        keysRegistered = true;
    }

    public static boolean isDebugVisible() {
        return WoodlightBuildConfig.DEBUG && debugVisible && isGameruleEnabled();
    }

    private static boolean isGameruleEnabled() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() == null || mc.getServer().getOverworld() == null) return false;
        return mc.getServer().getOverworld().getGameRules().getBoolean(Woodlightingstandards.STANDARDIZE_WOODLIGHT);
    }

    public static void onClientTick(MinecraftClient client) {
        if (!WoodlightBuildConfig.DEBUG) return;

        ensureKeysRegistered();

        if (client.world == null || client.player == null) return;

        if (!isGameruleEnabled()) {
            if (debugVisible) {
                SCAN_RESULT.clear();
                debugVisible = false;
            }
            return;
        }

        if (scanKey.wasPressed()) {
            debugVisible = !debugVisible;
            if (!debugVisible) SCAN_RESULT.clear();
        }

        if (debugVisible) {
            SCAN_RESULT.update();
        }
    }
}
