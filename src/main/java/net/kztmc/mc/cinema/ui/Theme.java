package net.kztmc.mc.cinema.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** UI全体の色・描画ヘルパー。見た目を変えたいときはここを触る。 */
public final class Theme {
    private Theme() {}

    public static final int PANEL = 0xEB0F1218;
    public static final int PANEL_BORDER = 0xFF262C38;
    public static final int SURFACE = 0xFF191D26;
    public static final int SURFACE_HOVER = 0xFF232938;
    public static final int BORDER = 0xFF303848;
    public static final int ACCENT = 0xFF5B9DFF;
    public static final int ACCENT_DIM = 0xFF2C5BA6;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int TEXT_DIM = 0xFF8D95A8;
    public static final int DANGER = 0xFFE5575F;
    public static final int DANGER_DIM = 0xFF3A1D23;
    public static final int GHOST_HOVER = 0x00232938; // alpha 0 から補間して出す

    /** 角を2pxだけ落とした簡易角丸 */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }

    /** 枠つきパネル */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        roundRect(g, x, y, w, h, PANEL_BORDER);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, PANEL);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= ((int) (ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    public static String fit(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String cut = font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width("…")));
        return cut + "…";
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
        g.text(font, s, cx - font.width(s) / 2, y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int color) {
        g.text(font, s, right - font.width(s), y, color, false);
    }
}