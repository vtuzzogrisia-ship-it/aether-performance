package com.aether.performance.mixins;

import com.aether.performance.gui.AetherSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aggiunge un pulsante "Aether Performance..." in fondo alla schermata Impostazioni video
 * di Minecraft, cosi' come fanno Sodium e Iris con le loro impostazioni. E' un trucco comune:
 * si aggiunge una finta opzione booleana (il suo testo del valore e' sempre vuoto, quindi si
 * vede solo l'etichetta) il cui "cambio di valore" (cioe' il clic) apre la nostra schermata
 * invece di attivare o disattivare qualcosa. require = 0: se in un futuro aggiornamento del
 * gioco questo metodo cambiasse, la mod continua a funzionare normalmente (si perde solo il
 * pulsante; restano comunque i tasti K e J).
 */
@Mixin(VideoOptionsScreen.class)
public abstract class VideoOptionsScreenMixin {

    @Shadow
    protected OptionListWidget body;

    @Inject(method = "addOptions", at = @At("TAIL"), require = 0)
    private void aether$addSettingsButton(CallbackInfo ci) {
        if (body == null) {
            return;
        }
        SimpleOption<Boolean> button = new SimpleOption<>(
                "options.aether-performance.open_settings",
                SimpleOption.constantTooltip(Text.translatable("options.aether-performance.open_settings.tooltip")),
                (optionText, value) -> Text.empty(),
                SimpleOption.BOOLEAN,
                false,
                value -> MinecraftClient.getInstance().setScreen(new AetherSettingsScreen((Screen) (Object) this))
        );
        body.addAll(new SimpleOption<?>[] { button });
    }
}
