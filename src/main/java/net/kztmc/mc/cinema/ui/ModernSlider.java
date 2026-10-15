package net.kztmc.mc.cinema.ui;

import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 自前スライダー。ラベル+値を上段、トラックを下段に描く。整数刻み。 */
public class ModernSlider extends AbstractWidget {
    private static final int SIDE = 7;

    private final String label;
    private final String unit;
    private final double min;
    private final double max;
    private final DoubleConsumer onChange;
    private double value;
    private boolean dragging;

    public ModernSlider(int x, int y, int w, int h, String label, String unit,
                        double min, double max, double value, DoubleConsumer onChange) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.value = Mth.clamp(value, min, max);
        this.onChange = onChange;
    }

    private void setFromMouse(double mouseX) {
        double t = (mouseX - (getX() + SIDE)) / (double) (getWidth() - SIDE * 2);
        t = Mth.clamp(t, 0.0, 1.0);
        value = Math.round(min + (max - min) * t);
        onChange.accept(value);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(), hov || dragging ? Theme.SURFACE_HOVER : Theme.SURFACE);
        Theme.text(g, font, label, getX() + SIDE, getY() + 3, Theme.TEXT_DIM);   // + 4 -> + 3
        Theme.textRight(g, font, Math.round(value) + unit, getX() + getWidth() - SIDE, getY() + 3, Theme.TEXT);   // + 4 -> + 3

        int trackY = getY() + getHeight() - 5;
        int trackL = getX() + SIDE;
        int trackR = getX() + getWidth() - SIDE;
        double t = (value - min) / (max - min);
        int thumbX = trackL + (int) Math.round((trackR - trackL) * t);

        g.fill(trackL, trackY, trackR, trackY + 2, Theme.BORDER);
        g.fill(trackL, trackY, thumbX, trackY + 2, Theme.ACCENT);
        int thumbColor = (hov || dragging) ? 0xFFFFFFFF : Theme.ACCENT;
        g.fill(thumbX - 2, trackY - 2, thumbX + 2, trackY + 4, thumbColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || event.button() != 0 || !isMouseOver(event.x(), event.y())) return false;
        dragging = true;
        setFromMouse(event.x());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return false;
        setFromMouse(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}