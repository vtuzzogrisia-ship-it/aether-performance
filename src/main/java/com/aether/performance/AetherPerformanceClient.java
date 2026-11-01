package com.aether.performance;

import com.aether.performance.diagnostics.AutoTuner;
import com.aether.performance.gui.AetherSettingsScreen;
import com.aether.performance.settings.ModSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class AetherPerformanceClient implements ClientModInitializer {
    /** Codici GLFW dei tasti K e J. */
    private static final int KEY_K = 75;
    private static final int KEY_J = 74;

    private static KeyBinding openSettingsKey;
    private static KeyBinding toggleOverlayKey;

    @Override
    public void onInitializeClient() {
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aether-performance.open_settings",
                InputUtil.Type.KEYSYM,
                KEY_K,
                KeyBinding.Category.MISC));

        toggleOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aether-performance.toggle_overlay",
                InputUtil.Type.KEYSYM,
                KEY_J,
                KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AetherSettingsScreen(null));
                }
            }
            while (toggleOverlayKey.wasPressed()) {
                ModSettings s = AetherPerformance.settings();
                s.showOverlay = !s.showOverlay;
                s.applyAndSave();
            }
            AutoTuner.tick(client);
        });
    }
}
