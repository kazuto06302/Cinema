package net.kztmc.mc.cinema.ui;

/**
 * 画面の寸法(init のたびに作る)。すべて Theme.u() 経由で、文字サイズ(Theme.FONT_PX)に連動して縮む。
 * 左右のパネルと下のパネルが、同じ寸法を使う。
 */
public record Layout(int m, int pad, int gap, int btnH, int sliderH, int th,
                     int panelW, int leftW, int leftX, int rightX, int panelY, int panelH) {

    public static Layout of(int screenW, int screenH) {
        int th = Theme.textH();
        int m = Theme.u(6);
        int pad = Theme.u(6);
        int gap = Theme.u(4);
        int panelW = Theme.u(210);
        int leftW = Theme.u(210);
        int btnH = Math.max(th + 3, Theme.u(14));
        int sliderH = th + Theme.u(11);
        return new Layout(m, pad, gap, btnH, sliderH, th, panelW, leftW,
                m, screenW - panelW - m, m, screenH - m * 2);
    }
}