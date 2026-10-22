package net.kztmc.mc.cinema.ui;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 自前スライダー。ラベル+値を上段、トラックを下段に描く。刻み幅を指定できる(既定は1)。 */
public class ModernSlider extends AbstractWidget {
    private final String label;
    private final String unit;
    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private final DoubleConsumer onChange;
    private double value;
    private boolean dragging;
    private java.util.function.DoubleFunction<String> formatter;
    /** 整数刻み */
    public ModernSlider(int x, int y, int w, int h, String label, String unit,
                        double min, double max, double value, DoubleConsumer onChange) {
        this(x, y, w, h, label, unit, min, max, 1.0, value, onChange);
    }

    /** step: 刻み幅(0.1 なら小数1桁で表示) */
    public ModernSlider(int x, int y, int w, int h, String label, String unit,
                        double min, double max, double step, double value, DoubleConsumer onChange) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = Math.max(0, (int) Math.ceil(-Math.log10(step) - 1e-9));
        this.onChange = onChange;
        this.value = snap(value);
    }

    private double snap(double v) {
        double snapped = Math.round((v - min) / step) * step + min;
        return Mth.clamp(snapped, min, max);
    }

    private String valueText() {
        if (formatter != null) return formatter.apply(value);
        return String.format(Locale.ROOT, "%." + decimals + "f", value) + unit;
    }

    private void setFromMouse(double mouseX) {
        int side = Theme.u(7);
        double t = (mouseX - (getX() + side)) / (double) (getWidth() - side * 2);
        t = Mth.clamp(t, 0.0, 1.0);
        value = snap(min + (max - min) * t);
        onChange.accept(value);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        int side = Theme.u(7);

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(), hov || dragging ? Theme.SURFACE_HOVER : Theme.SURFACE);
        Theme.text(g, font, label, getX() + side, getY() + Theme.u(3), Theme.TEXT_DIM);
        Theme.textRight(g, font, valueText(), getX() + getWidth() - side, getY() + Theme.u(3), Theme.TEXT);

        int tt = Math.max(1, Theme.u(2));       // トラックの太さ
        int tw = Math.max(1, Theme.u(2));       // つまみの半幅
        int trackY = getY() + getHeight() - Theme.u(6);
        int trackL = getX() + side;
        int trackR = getX() + getWidth() - side;
        double t = (value - min) / (max - min);
        int thumbX = trackL + (int) Math.round((trackR - trackL) * t);

        g.fill(trackL, trackY, trackR, trackY + tt, Theme.BORDER);
        g.fill(trackL, trackY, thumbX, trackY + tt, Theme.ACCENT);
        int thumbColor = (hov || dragging) ? 0xFFFFFFFF : Theme.ACCENT;
        g.fill(thumbX - tw, trackY - tw, thumbX + tw, trackY + tt + tw, thumbColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
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

    /** ホイールで刻み幅ずつ微調整(細かい値を狙うとき用) */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mx, my) || scrollY == 0) return false;
        value = snap(value + Math.signum(scrollY) * step);
        onChange.accept(value);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    public ModernSlider setValueFormatter(java.util.function.DoubleFunction<String> formatter) {
        this.formatter = formatter;
        return this;
    }
}