package net.kztmc.mc.cinema.ui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** UI全体の色・描画ヘルパー。見た目を変えたいときはここを触る。 */
public final class Theme {
    private Theme() {}

    public static final float FONT_PX = 17f;

    public static final int PANEL = 0xCC0F1218;
    public static final int PANEL_BORDER = 0xFF262C38;
    public static final int SURFACE = 0xE6191D26;
    public static final int SURFACE_HOVER = 0xE6232938;
    public static final int BORDER = 0xFF303848;
    public static final int ACCENT = 0xFF5B9DFF;
    public static final int ACCENT_DIM = 0xFF2C5BA6;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int TEXT_DIM = 0xFF8D95A8;
    public static final int DANGER = 0xE6E5575F;
    public static final int DANGER_DIM = 0xE63A1D23;
    public static final int GHOST_HOVER = 0x00232938; // alpha 0 から補間して出す
    public static final int SECTION = 0xFF7FA8E0;

    public static final int CHANGED = 0xFFFFD34D;        // 既定値から変えた項目の文字(黄)
    public static final int SECTION_LINE = 0x557FA8E0;   // 見出しの横の線
    public static final int SECTION_CARD = 0x14FFFFFF;   // セクションの背景(白の8%)
    public static final float HEADING_SCALE = 1f;     // 見出しの文字の倍率

    // ---- 絵文字つきのラベル ----
    private static final float ICON_BOX = 9f;    // 絵文字を入れる箱の幅(8pxフォント基準)
    private static final float ICON_GAP = 3f;    // 箱と文字の間(同じ基準)
    private static final float ICON_DX = 0f;     // 絵文字の位置の微調整(右が+、8px基準)
    private static final float ICON_DY = 0.5f;   // 絵文字の位置の微調整(下が+、8px基準)

    /** 文字に、マイクラ標準の影を付けるか(false にすると、元の見た目に戻る) */
    public static final boolean TEXT_SHADOW = true;
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

    public record IconLabel(String icon, String text) {}

    public static boolean probeAnyButton;

    public static boolean isClick(int button) {
        return probeAnyButton || button == 0 || button == 1;   // 既存の判定の前に、フラグを足す
    }
    // ---- 描画 ----

    /** 角を落とした簡易角丸(小さいときは1px)。半透明の色でも、濃さが変わらないように、塗る範囲が重ならない */
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

    /** 枠つきパネル */
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
        g.text(font, s, 0, 0, color, TEXT_SHADOW);   // 標準の影(元の色を暗くして、右下に1ずらす)
        pose.popMatrix();
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
        text(g, font, s, cx - textWidth(font, s) / 2, y, color);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int color) {
        text(g, font, s, right - textWidth(font, s), y, color);
    }

    public static int textWidth(Font font, String s, float scale) {
        return Math.round(font.width(s) * scale);
    }

    public static String fit(Font font, String s, int maxWidth, float scale) {
        if (textWidth(font, s, scale) <= maxWidth) return s;
        int budget = (int) ((maxWidth - textWidth(font, "…", scale)) / scale);
        return font.plainSubstrByWidth(s, Math.max(0, budget)) + "…";
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color, float scale) {
        text(g, font, s, cx - textWidth(font, s, scale) / 2, y, color, scale);
    }

    public static boolean isRightClick(int button) {
        return button == 2 || button == 3;
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

    /** 絵文字の箱 + 隙間の幅(GUI単位)。絵文字つきのラベルは、文字がこの分だけ右から始まる */
    public static int iconSlot() {
        return Math.round((ICON_BOX + ICON_GAP) * fontScale());
    }

    /** 絵文字つきのラベルを描く。絵文字は箱の中央、文字は箱の右から。maxWidth を超えるぶんは、文字を省略する */
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
}