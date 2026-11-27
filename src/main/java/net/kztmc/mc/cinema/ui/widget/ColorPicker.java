package net.kztmc.mc.cinema.ui.widget;

import java.util.function.IntConsumer;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** カラーパレット: 色の面(彩度×明るさ)+ 色相バー + 見本の行。onChange には RGB(0xRRGGBB)が渡る */
public class ColorPicker extends AbstractWidget {
    private enum Drag { NONE, AREA, HUE }

    private final int[] presets;
    private final IntConsumer onChange;
    private float hue, sat, val;
    private Drag drag = Drag.NONE;

    private int[] columns = new int[0];   // 色の面の、列ごとの上端の色(明るさ100%)
    private float columnsHue = -1f;       // その色を計算したときの色相
    private int[] hueColors = new int[0];   // 色相バーの、境目ごとの色(行数 + 1 個)

    public ColorPicker(int x, int y, int width, int height, int rgb, int[] presets, IntConsumer onChange) {
        super(x, y, width, height, Component.empty());
        this.presets = presets;
        this.onChange = onChange;
        setRgb(rgb);
    }

    // ---- 色の読み書き ----

    public int rgb() {
        return Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
    }

    /** 外から色を合わせる(onChange は呼ばない)。彩度か明るさが0のときは、色相を保つ */
    public void setRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float d = max - Math.min(r, Math.min(g, b));
        val = max;
        sat = max == 0f ? 0f : d / max;
        if (d > 1e-6f) {
            float h;
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            hue = h / 6f;
            if (hue < 0f) hue += 1f;
        }
    }

    // ---- 寸法 ----

    private int gap() { return Math.max(2, Theme.u(4)); }
    private int hueW() { return Theme.u(14); }
    private int swatchH() { return Theme.u(14); }
    private int areaW() { return getWidth() - hueW() - gap(); }
    private int areaH() { return getHeight() - swatchH() - gap(); }
    private int swatchGap() { return Math.max(2, Theme.u(3)); }
    private int swatchW() { return (getWidth() - swatchGap() * (presets.length - 1)) / presets.length; }

    // ---- 描画 ----

    /** 色の面の、各列の上端の色。色相か幅が変わったときだけ計算し直す */
    private int[] columnColors(int width) {
        if (columns.length != width || columnsHue != hue) {
            columns = new int[width];
            for (int i = 0; i < width; i++) {
                columns[i] = 0xFF000000 | Mth.hsvToRgb(hue, i / (float) Math.max(1, width - 1), 1f);
            }
            columnsHue = hue;
        }
        return columns;
    }

    /** 色相バーの、各行の境目の色。高さが変わったときだけ計算し直す */
    private int[] hueColors(int height) {
        if (hueColors.length != height + 1) {
            hueColors = new int[height + 1];
            for (int i = 0; i <= height; i++) {
                hueColors[i] = 0xFF000000 | Mth.hsvToRgb(i / (float) height, 1f, 1f);   // 0〜360度
            }
        }
        return hueColors;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = getX(), y = getY(), aw = areaW(), ah = areaH();

        // 色の面: 1列ずつ、上(明るさ100%)から下(黒)へのグラデーション
        int[] colors = columnColors(aw);
        for (int i = 0; i < aw; i++) {
            g.fillGradient(x + i, y, x + i + 1, y + ah, colors[i], 0xFF000000);
        }
        int px = x + Math.round(sat * (aw - 1));
        int py = y + Math.round((1f - val) * (ah - 1));
        g.outline(px - 3, py - 3, 7, 7, 0xFF000000);
        g.outline(px - 2, py - 2, 5, 5, 0xFFFFFFFF);

        // 色相バー: 1行ずつ、その行の上端の色から、下端の色へのグラデーション
        int hx = x + aw + gap();
        int hw = hueW();
        int[] hues = hueColors(ah);
        for (int r = 0; r < ah; r++) {
            g.fillGradient(hx, y + r, hx + hw, y + r + 1, hues[r], hues[r + 1]);
        }
        int hy = y + Math.round(hue * (ah - 1));
        g.outline(hx - 1, hy - 2, hw + 2, 5, 0xFF000000);
        g.outline(hx, hy - 1, hw, 3, 0xFFFFFFFF);

        // 見本の行
        int sy = y + ah + gap();
        int sw = swatchW();
        int sh = swatchH();
        int current = rgb();
        for (int i = 0; i < presets.length; i++) {
            int sx = x + i * (sw + swatchGap());
            g.fill(sx, sy, sx + sw, sy + sh, 0xFF000000 | presets[i]);
            boolean hov = mx >= sx && mx < sx + sw && my >= sy && my < sy + sh;
            if (presets[i] == current) g.outline(sx - 1, sy - 1, sw + 2, sh + 2, 0xFFFFFFFF);
            else if (hov) g.outline(sx - 1, sy - 1, sw + 2, sh + 2, Theme.TEXT_DIM);
        }
    }

    // ---- 入力(ドラッグは、画面から drag / release を直接呼ぶ) ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        double mx = event.x();
        double my = event.y();

        if (my >= getY() + areaH() + gap()) {   // 見本の行
            int index = (int) ((mx - getX()) / (swatchW() + swatchGap()));
            int left = getX() + index * (swatchW() + swatchGap());
            if (index >= 0 && index < presets.length && mx < left + swatchW()) {
                setRgb(presets[index]);
                onChange.accept(rgb());
                UiSounds.click();
            }
            return true;
        }
        if (mx < getX() + areaW()) drag = Drag.AREA;
        else if (mx >= getX() + areaW() + gap()) drag = Drag.HUE;
        else return true;
        UiSounds.click();
        drag(mx, my);
        return true;
    }

    public boolean dragging() {
        return drag != Drag.NONE;
    }

    public void drag(double mx, double my) {
        float fx = (float) Mth.clamp((mx - getX()) / Math.max(1, areaW() - 1), 0.0, 1.0);
        float fy = (float) Mth.clamp((my - getY()) / Math.max(1, areaH() - 1), 0.0, 1.0);
        if (drag == Drag.AREA) {
            sat = fx;
            val = 1f - fy;
        } else if (drag == Drag.HUE) {
            hue = Math.min(0.999f, fy);
        } else {
            return;
        }
        onChange.accept(rgb());
    }

    public void release() {
        drag = Drag.NONE;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}