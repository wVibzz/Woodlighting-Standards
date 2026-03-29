package net.vibzz.woodlightingstandards.mixin.client;

import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameOptions.class)
public interface GameOptionsAccessor {
    @Accessor("keysAll")
    KeyBinding[] getKeysAll();

    @Mutable
    @Accessor("keysAll")
    void setKeysAll(KeyBinding[] keys);
}
