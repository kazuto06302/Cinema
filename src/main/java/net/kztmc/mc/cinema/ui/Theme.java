package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** UI全体の色・描画ヘルパー。見た目を変えたいときはここを触る。 */
public final class Theme {
    private Theme() {}

    public static final float FONT_PX = 18f;

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

    // ---- サイズ(GUIスケールに連動) ----

    /** バニラのフォントは8px。GUIスケール込みで FONT_PX になるような拡大率 */
    public static float fontScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return Mth.clamp((float) (FONT_PX / (8.0 * gui)), 0.4f, 1.0f);
    }

    /** 8px基準の寸法を、現在の文字サイズに合わせて縮める */
    public static int u(int base) {
        return Math.max(1, Math.round(base * fontScale()));
    }

    /** 描画される文字の高さ(GUI単位) */
    public static int textH() {
        return Math.max(3, Math.round(8 * fontScale()));
    }

    /**
     * クリックとして扱うボタン番号。
     * ログでは物理クリックが 1 や 3 で届いていたため、0 と 1 を許可している。
     */
    public static boolean isClick(int button) {
        return button == 0 || button == 1;
    }

    // ---- 描画 ----

    /** 角を落とした簡易角丸(小さいときは1px) */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        int r = fontScale() < 0.7f ? 1 : 2;
        g.fill(x + r, y, x + w - r, y + h, color);
        if (r == 2) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + r, x + w, y + h - r, color);
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

    /** 拡大率を反映したテキスト幅(GUI単位) */
    public static int textWidth(Font font, String s) {
        return Math.round(font.width(s) * fontScale());
    }

    public static String fit(Font font, String s, int maxWidth) {
        if (textWidth(font, s) <= maxWidth) return s;
        int budget = (int) ((maxWidth - textWidth(font, "…")) / fontScale());
        return font.plainSubstrByWidth(s, Math.max(0, budget)) + "…";
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        text(g, font, s, x, y, color, fontScale());
    }

    /** 倍率を指定して描く(入力欄など、UI標準より少し小さくしたいとき用) */
    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(scale, scale);
        g.text(font, s, 0, 0, color, false);
        pose.popMatrix();
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
        text(g, font, s, cx - textWidth(font, s) / 2, y, color);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int color) {
        text(g, font, s, right - textWidth(font, s), y, color);
    }
}