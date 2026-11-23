package net.kztmc.mc.cinema.ui.panel.right;

import net.kztmc.mc.cinema.ui.core.Layout;
import net.kztmc.mc.cinema.ui.core.PanelHost;
import net.kztmc.mc.cinema.ui.core.Tr;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 画面下中央の、ギズモの操作モード(移動・回転・拡大)の切り替え */
public final class GizmoModeBar {
    private boolean shown;
    private int x, y, w, h;

    public void build(Layout lay, PanelHost host, boolean show) {
        shown = show;
        if (!show) return;

        String[] names = {Tr.get("cinema.ui.parts.mode.move"), Tr.get("cinema.ui.parts.mode.rotate"),
                Tr.get("cinema.ui.parts.mode.scale")};
        int pad = lay.pad();
        int gap = lay.gap();
        int bw = Theme.u(56);
        int bh = lay.btnH();

        w = pad * 2 + bw * 3 + gap * 2;
        h = pad * 2 + bh;
        x = (host.screen().width - w) / 2;
        y = host.screen().height - lay.m() - h;

        for (int i = 0; i < 3; i++) {
            PartGizmo.Mode mode = PartGizmo.Mode.values()[i];
            host.add(new ModernButton(x + pad + i * (bw + gap), y + pad, bw, bh, names[i],
                    ModernButton.Style.DEFAULT, () -> {
                PartGizmo.mode = mode;
                host.refresh();
            }).setSelected(PartGizmo.mode == mode));
        }
    }

    public boolean contains(double mx, double my) {
        return shown && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void extractBackground(GuiGraphicsExtractor g) {
        if (shown) Theme.panel(g, x, y, w, h);
    }
}