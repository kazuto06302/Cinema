package net.kztmc.mc.cinema.ui.widget;

import net.kztmc.mc.cinema.ui.panel.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// 独自のツールチップ(角丸なしの四角と、縁の線だけ)
//   部品が request() で文字を頼み、画面が、描画の最後に draw() で、まとめて1つだけ描く
public final class Tooltips {
    private Tooltips() {}

    private static final long DELAY_MS = 300;                // 乗せてから、出るまで
    private static final int BACKGROUND = 0xF2101319;
    private static final int BORDER = 0xFF4B5568;

    private static Object owner;     // いま、乗っている部品
    private static long since;       // その部品に乗り始めた時刻
    private static String text;
    private static boolean requested;

    // 部品が、マウスが乗っている間、毎フレーム呼ぶ
    public static void request(Object source, String tip) {
        if (tip == null || tip.isEmpty()) return;
        if (owner != source) {
            owner = source;
            since = System.currentTimeMillis();
        }
        text = tip;
        requested = true;
    }

    // 画面が、描画の最後に呼ぶ(文字の改行は \n)
    public static void draw(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
        if (!requested) owner = null;   // 乗っていなければ、次に乗ったとき、数え直す
        boolean show = requested && System.currentTimeMillis() - since >= DELAY_MS;
        String tip = text;
        requested = false;
        if (!show || tip == null) return;

        String[] lines = tip.split("\n");
        int pad = Theme.u(5);
        int lineH = Theme.textH() + Theme.u(2);
        int width = 0;
        for (String line : lines) width = Math.max(width, Theme.textWidth(font, line));
        int w = width + pad * 2;
        int h = lines.length * lineH - Theme.u(2) + pad * 2;

        int x = mx + Theme.u(10);
        int y = my + Theme.u(10);
        if (x + w > screenW - 2) x = Math.max(2, mx - w - Theme.u(4));   // 右にはみ出すなら、カーソルの左へ
        if (y + h > screenH - 2) y = Math.max(2, screenH - h - 2);

        g.nextStratum();   // ほかの部品より、手前に描く
        g.fill(x, y, x + w, y + h, BACKGROUND);
        g.outline(x, y, w, h, BORDER);
        for (int i = 0; i < lines.length; i++) {
            Theme.text(g, font, lines[i], x + pad, y + pad + i * lineH, Theme.TEXT);
        }
    }
}