package net.vibzz.woodlightingstandards;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.GameRules;

public class Woodlightingstandards implements ModInitializer {

    public static GameRules.Key<GameRules.BooleanRule> STANDARDIZE_WOODLIGHT;

    @Override
    public void onInitialize() {
        STANDARDIZE_WOODLIGHT = GameRules.register(
                "standardizeWoodlight",
                GameRules.Category.MISC,
                GameRules.BooleanRule.create(true)
        );
    }
}
