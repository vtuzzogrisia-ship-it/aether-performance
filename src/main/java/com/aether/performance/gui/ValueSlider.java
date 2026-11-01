package com.aether.performance.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Cursore con valori interi a scatti, collegato a un'impostazione. */
public class ValueSlider extends SliderWidget {
    private final int min;
    private final int max;
    private final int step;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final IntFunction<String> format;

    public ValueSlider(int x, int y, int width, int height, int min, int max, int step,
                       IntSupplier getter, IntConsumer setter, IntFunction<String> format) {
        super(x, y, width, height, Text.empty(), toSlider(getter.getAsInt(), min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.format = format;
        updateMessage();
    }

    private static double toSlider(int value, int min, int max) {
        return Math.max(0.0, Math.min(1.0, (value - min) / (double) (max - min)));
    }

    @Override
    protected void updateMessage() {
        if (format == null || getter == null) {
            return;
        }
        setMessage(Text.literal(format.apply(getter.getAsInt())));
    }

    @Override
    protected void applyValue() {
        int raw = min + (int) Math.round(this.value * (max - min) / (double) step) * step;
        raw = Math.max(min, Math.min(max, raw));
        this.value = toSlider(raw, min, max);
        setter.accept(raw);
    }

    /** Rilegge il valore dall'impostazione (dopo un cambio di modalità o un reset). */
    public void sync() {
        this.value = toSlider(getter.getAsInt(), min, max);
        updateMessage();
    }
}
