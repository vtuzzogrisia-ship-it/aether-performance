package com.aether.performance;

import com.aether.performance.gui.AetherSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class AetherPerformanceClient implements ClientModInitializer {
    /** Codice GLFW del tasto K. */
    private static final int KEY_K = 75;

    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aether-performance.open_settings",
                InputUtil.Type.KEYSYM,
                KEY_K,
                KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AetherSettingsScreen(null));
                }
            }
        });
    }
}
