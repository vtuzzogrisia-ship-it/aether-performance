package com.aether.performance.diagnostics;

import com.aether.performance.AetherPerformance;
import com.aether.performance.settings.ModSettings;
import com.aether.performance.utils.FailSafe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;

/**
 * HUD da combattimento in stile Lunar Client: timer TNT, durabilità armatura ed effetti
 * pozione, ping/FPS compatti, indicatore sprint. Niente che dia un vantaggio nascosto: sono
 * solo informazioni che potresti già vedere tu stesso, mostrate in modo più comodo. Ogni pezzo
 * si attiva/disattiva da solo dalla scheda PvP, e se qualcosa va storto si disattiva da sé
 * senza toccare gli altri (vedi {@link FailSafe}).
 */
public final class CombatHud {
    private static final int BG = 0xA0111722;
    private static final int ACCENT = 0xFF5AC8FA;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFB9C2CC;
    private static final int ORANGE = 0xFFFFA500;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF6FCF6F;
    private static final String[] ROMAN = { "", "II", "III", "IV", "V" };

    private CombatHud() {
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        ModSettings s = AetherPerformance.settings();
        if (s.pvpTntTimer) {
            run("pvp.tnt-timer", () -> renderTntTimer(context, client));
        }
        if (s.pvpCombatHud) {
            run("pvp.combat-hud", () -> renderCombatHud(context, client));
        }
        if (s.pvpCompactHud) {
            run("pvp.compact-hud", () -> renderCompactHud(context, client));
        }
        if (s.pvpSprintIndicator) {
            run("pvp.sprint-indicator", () -> renderSprintIndicator(context, client));
        }
    }

    private static void run(String feature, Runnable action) {
        if (FailSafe.isDisabled(feature)) {
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            FailSafe.guard(feature, t);
        }
    }

    /** Conto alla rovescia sopra ogni TNT accesa entro 32 blocchi (al massimo 5 in lista). */
    private static void renderTntTimer(DrawContext context, MinecraftClient client) {
        PlayerEntity me = client.player;
        List<TntEntity> nearby = client.world.getEntitiesByClass(TntEntity.class,
                me.getBoundingBox().expand(32), e -> true);
        if (nearby.isEmpty()) {
            return;
        }
        nearby.sort(Comparator.comparingInt(TntEntity::getFuse));
        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int x = sw / 2 - 36;
        int y = 6;
        int shown = 0;
        for (TntEntity tnt : nearby) {
            if (shown >= 5) {
                break;
            }
            double seconds = tnt.getFuse() / 20.0;
            String line = String.format("TNT: %.1fs", seconds);
            int color = seconds < 1.0 ? RED : (seconds < 2.0 ? ORANGE : WHITE);
            int w = tr.getWidth(line);
            context.fill(x - 4, y - 2, x + w + 4, y + 10, BG);
            context.drawTextWithShadow(tr, line, x, y, color);
            y += 13;
            shown++;
        }
    }

    /** Durabilità dei pezzi di armatura ed effetti pozione attivi, in alto a destra. */
    private static void renderCombatHud(DrawContext context, MinecraftClient client) {
        PlayerEntity me = client.player;
        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int x = sw - 6;
        int y = 6;

        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        String[] names = { "Elmo", "Corazza", "Gambali", "Stivali" };
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = me.getEquippedStack(slots[i]);
            if (stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }
            int max = stack.getMaxDamage();
            int left = max - stack.getDamage();
            int pct = max <= 0 ? 100 : Math.round(left * 100f / max);
            String line = names[i] + ": " + pct + "%";
            int color = pct <= 15 ? RED : (pct <= 40 ? ORANGE : GREEN);
            int w = tr.getWidth(line);
            context.fill(x - w - 6, y - 2, x + 2, y + 10, BG);
            context.drawTextWithShadow(tr, line, x - w, y, color);
            y += 12;
        }

        int shown = 0;
        for (StatusEffectInstance effect : me.getStatusEffects()) {
            if (shown >= 6) {
                break;
            }
            String name = Text.translatable(effect.getTranslationKey()).getString();
            int amp = effect.getAmplifier();
            String roman = amp > 0 && amp < ROMAN.length ? " " + ROMAN[amp] : "";
            int secs = Math.max(0, effect.getDuration() / 20);
            String line = name + roman + " " + (secs / 60) + ":" + String.format("%02d", secs % 60);
            int w = tr.getWidth(line);
            context.fill(x - w - 6, y - 2, x + 2, y + 10, BG);
            context.drawTextWithShadow(tr, line, x - w, y, WHITE);
            y += 12;
            shown++;
        }
    }

    /** Versione minimale dell'overlay diagnostico: solo ping e FPS, in basso a destra. */
    private static void renderCompactHud(DrawContext context, MinecraftClient client) {
        TextRenderer tr = client.textRenderer;
        double fps = AetherPerformance.monitor().averageFps();
        int ping = AetherPerformance.monitor().pingMs();
        String line = (fps > 0 ? String.format("%.0f FPS", fps) : "-- FPS") + (ping >= 0 ? "  " + ping + "ms" : "");
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int w = tr.getWidth(line);
        int x = sw - 6 - w;
        int y = sh - 14;
        context.fill(x - 4, y - 2, x + w + 4, y + 10, BG);
        context.drawTextWithShadow(tr, line, x, y, ACCENT);
    }

    /** Piccola scritta mentre stai sprintando, per non perderlo di vista in un combattimento. */
    private static void renderSprintIndicator(DrawContext context, MinecraftClient client) {
        if (!client.player.isSprinting()) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        String line = "SPRINT";
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int w = tr.getWidth(line);
        int x = sw / 2 - w / 2;
        int y = sh / 2 + 24;
        context.drawTextWithShadow(tr, line, x, y, 0xFFFFD700);
    }
}
