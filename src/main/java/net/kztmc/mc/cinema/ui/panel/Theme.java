package net.kztmc.mc.cinema.ui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** UI全体の色・描画ヘルパー。見た目を変えたいときはここを触る。 */
public final class Theme {
    private Theme() {}

    public static final float FONT_PX = 18f;
    // ---- 色(setThemeColor で再計算される) ----
    public static int PANEL, PANEL_BORDER, SURFACE, SURFACE_HOVER, BORDER;
    public static int ACCENT, ACCENT_DIM;
    public static int TEXT, TEXT_DIM, TEXT_NOTE;
    public static int GHOST_HOVER, SECTION, SECTION_LINE;
    public static int POPUP, POPUP_HOVER;
    public static int SELECTED, CARD, TOOLTIP_BG, TOOLTIP_BORDER;   // 他クラスに直書きされていた色
    // テーマに関係しない色(固定)
    public static final int DANGER = 0xE6E5575F;
    public static final int DANGER_DIM = 0xE63A1D23;
    public static final int CHANGED = 0xFFFFD34D;
    public static final int SLOT = 0x66000000;
    public static final int SECTION_CARD = 0x14FFFFFF;

    private static final int DEFAULT_THEME = 0x5B9DFF;
    private static final float MUTE = 0.5f;   // 灰色寄りにする割合(大きいほど色味が薄い)
    private static int base = DEFAULT_THEME;


    public static final float HEADING_SCALE = 1f;     // 見出しの文字の倍率

    private static final float ICON_BOX = 9f;    // 絵文字を入れる箱の幅(8pxフォント基準)
    private static final float ICON_GAP = 3f;    // 箱と文字の間(同じ基準)
    private static final float ICON_DX = 0f;     // 絵文字の位置の微調整(右が+、8px基準)
    private static final float ICON_DY = 0.5f;   // 絵文字の位置の微調整(下が+、8px基準)

    public static final boolean TEXT_SHADOW = true;

    public static boolean probeAnyButton;

    // draw
    public static float fontScale() {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return Mth.clamp((float) (FONT_PX / (8.0 * gui)), 0.4f, 1.0f);
    }
    public static int u(int base) {
        return Math.max(1, Math.round(base * fontScale()));
    }

    public record IconLabel(String icon, String text) {}
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        roundRect(g, x, y, w, h, PANEL);
        g.fill(x + 2, y, x + w - 2, y + 1, PANEL_BORDER);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, PANEL_BORDER);
        g.fill(x, y + 2, x + 1, y + h - 2, PANEL_BORDER);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, PANEL_BORDER);
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
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (((color >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) return;
        int r = fontScale() < 0.7f ? 1 : 2;
        if (w <= r * 2 || h <= r * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (r == 2) {
            g.fill(x + 2, y, x + w - 2, y + 1, color);               // 1行目
            g.fill(x + 1, y + 1, x + w - 1, y + 2, color);           // 2行目
            g.fill(x, y + 2, x + w, y + h - 2, color);               // 中央
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);   // 下から2行目
            g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);       // 最終行
        } else {
            g.fill(x + 1, y, x + w - 1, y + 1, color);
            g.fill(x, y + 1, x + w, y + h - 1, color);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        }
    }


    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        text(g, font, s, x, y, color, fontScale());
    }
    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, float scale) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(scale, scale);
        g.text(font, s, 0, 0, color, TEXT_SHADOW);   // 標準の影(元の色を暗くして、右下に1ずらす)
        pose.popMatrix();
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
        text(g, font, s, cx - textWidth(font, s) / 2, y, color);
    }
    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color, float scale) {
        text(g, font, s, cx - textWidth(font, s, scale) / 2, y, color, scale);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int color) {
        text(g, font, s, right - textWidth(font, s), y, color);
    }

    public static int textH() {
        return Math.max(3, Math.round(8 * fontScale()));
    }
    public static int textWidth(Font font, String s) {
        return Math.round(font.width(s) * fontScale());
    }
    public static int textWidth(Font font, String s, float scale) {
        return Math.round(font.width(s) * scale);
    }
    public static String fit(Font font, String s, int maxWidth, float scale) {
        if (textWidth(font, s, scale) <= maxWidth) return s;
        int budget = (int) ((maxWidth - textWidth(font, "…", scale)) / scale);
        return font.plainSubstrByWidth(s, Math.max(0, budget)) + "…";
    }
    public static String fit(Font font, String s, int maxWidth) {
        if (textWidth(font, s) <= maxWidth) return s;
        int budget = (int) ((maxWidth - textWidth(font, "…")) / fontScale());
        return font.plainSubstrByWidth(s, Math.max(0, budget)) + "…";
    }

    public static IconLabel splitIcon(String label) {
        int space = label.indexOf(' ');
        if (space <= 0 || space >= label.length() - 1) return new IconLabel(null, label);
        String head = label.substring(0, space);
        if (head.codePointCount(0, head.length()) > 3) return new IconLabel(null, label);
        for (int i = 0; i < head.length(); ) {
            int cp = head.codePointAt(i);
            if (cp < 0x80 || Character.isLetterOrDigit(cp)) return new IconLabel(null, label);
            i += Character.charCount(cp);
        }
        return new IconLabel(head, label.substring(space + 1).stripLeading());
    }
    public static int iconSlot() {
        return Math.round((ICON_BOX + ICON_GAP) * fontScale());
    }
    public static void labelWithIcon(GuiGraphicsExtractor g, Font font, String label, int x, int y, int color, int maxWidth) {
        IconLabel l = splitIcon(label);
        if (l.icon() == null) {
            text(g, font, fit(font, label, maxWidth), x, y, color);
            return;
        }
        float fs = fontScale();
        float w = font.width(l.icon());                          // 8px基準の幅
        float scale = fs * (w > ICON_BOX ? ICON_BOX / w : 1f);   // 広い絵文字だけ、箱に収まるまで縮める
        float ix = x + (ICON_BOX * fs - w * scale) / 2f + ICON_DX * fs;   // 箱の中で、横を中央に
        float iy = y + (8f * fs - 8f * scale) / 2f + ICON_DY * fs;        // 縮めたぶん、縦も文字の中央に

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(ix, iy);
        pose.scale(scale, scale);
        g.text(font, l.icon(), 0, 0, color, TEXT_SHADOW);
        pose.popMatrix();

        int slot = iconSlot();
        text(g, font, fit(font, l.text(), Math.max(0, maxWidth - slot)), x + slot, y, color);
    }

    public static void heading(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        text(g, font, s, x, y, color, fontScale() * HEADING_SCALE);
    }
    public static int headingWidth(Font font, String s) {
        return Math.round(textWidth(font, s) * HEADING_SCALE);
    }

    public static boolean isClick(int button) {
        return probeAnyButton || button == 0 || button == 1;   // 既存の判定の前に、フラグを足す
    }
    public static boolean isRightClick(int button) {
        return button == 2 || button == 3;
    }

    static { setThemeColor(DEFAULT_THEME); }

    public static int themeColor() { return base; }

    /** テーマカラー(RGB)を指定して、全部の色を計算し直す */
    public static void setThemeColor(int rgb) {
        base = rgb & 0xFFFFFF;
        int muted = mix(base, 0x808080, MUTE);   // 背景・文字用: 色味を薄めたベース

        // アクセント系: ベース色そのまま / 黒を混ぜる / 白を混ぜる
        ACCENT        = tone(base, 0f, 0f, 0xFF);
        ACCENT_DIM    = tone(base, 0f, 0.35f, 0xFF);
        SELECTED      = tone(base, 0f, 0.60f, 0xFF);
        SECTION       = tone(base, 0.30f, 0f, 0xFF);
        SECTION_LINE  = tone(base, 0.30f, 0f, 0x55);

        // 背景系: 黒の割合が大きいほど暗い。alpha で透け具合を決める
        PANEL          = tone(muted, 0f, 0.88f, 0xE6);
        PANEL_BORDER   = tone(muted, 0f, 0.66f, 0xFF);
        SURFACE        = tone(muted, 0f, 0.78f, 0x00);
        SURFACE_HOVER  = tone(muted, 0f, 0.68f, 0xE6);
        GHOST_HOVER    = tone(muted, 0f, 0.68f, 0x00);   // alpha 0 から補間して出す
        BORDER         = tone(muted, 0f, 0.58f, 0xFF);
        POPUP          = tone(muted, 0f, 0.78f, 0xFF);
        POPUP_HOVER    = tone(muted, 0f, 0.68f, 0xFF);
        CARD           = tone(muted, 0f, 0.75f, 0xFF);
        TOOLTIP_BG     = tone(muted, 0f, 0.90f, 0xF2);
        TOOLTIP_BORDER = tone(muted, 0f, 0.45f, 0xFF);

        // 文字系: 白の割合が大きいほど明るい
        TEXT      = tone(muted, 0.88f, 0f, 0xFF);
        TEXT_DIM  = tone(muted, 0.55f, 0.26f, 0xFF);
        TEXT_NOTE = tone(muted, 0.12f, 0f, 0xFF);
    }

    /** a から b へ t(0〜1)だけ混ぜる。RGB のみ */
    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** 白を white、黒を black の割合で混ぜて、alpha(0〜255)をつける */
    private static int tone(int rgb, float white, float black, int alpha) {
        int c = mix(mix(rgb, 0xFFFFFF, white), 0x000000, black);
        return (alpha << 24) | c;
    }

}