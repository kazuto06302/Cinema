package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** 右クリックで開く、小さいメニュー。外をクリックすると閉じる(そのクリックは、そのまま下へ通す)。 */
public final class ContextMenu {
    public record Entry(String label, Runnable action, boolean danger) {}

    private final List<Entry> entries = new ArrayList<>();
    private boolean open;
    private int x, y, w, h, rowH;

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void open(Font font, Layout lay, int screenW, int screenH, int mouseX, int mouseY, List<Entry> list) {
        entries.clear();
        entries.addAll(list);
        rowH = lay.btnH() + Theme.u(2);
        int widest = 0;
        for (Entry e : entries) widest = Math.max(widest, Theme.textWidth(font, e.label()));
        w = Math.max(Theme.u(80), widest + Theme.u(20));
        h = rowH * entries.size() + 2;
        x = Math.max(2, Math.min(mouseX, screenW - w - 2));
        y = Math.max(2, Math.min(mouseY, screenH - h - 2));
        open = true;
    }

    private boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void extract(GuiGraphicsExtractor g, Font font, int mx, int my) {
        if (!open) return;
        Theme.roundRect(g, x - 1, y - 1, w + 2, h + 2, Theme.BORDER);
        Theme.roundRect(g, x, y, w, h, Theme.SURFACE);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int ry = y + 1 + i * rowH;
            boolean hov = mx >= x && mx < x + w && my >= ry && my < ry + rowH;
            if (hov) g.fill(x + 1, ry, x + w - 1, ry + rowH, Theme.SURFACE_HOVER);
            Theme.text(g, font, e.label(), x + Theme.u(10), ry + (rowH - Theme.textH()) / 2,
                    e.danger() ? 0xFFE5534B : Theme.TEXT);
        }
    }

    /** メニューの中のクリックは、ここで受ける(true)。外のクリックは、閉じて、false を返す(下へ通す) */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!open) return false;
        if (!contains(event.x(), event.y())) {
            close();
            return false;
        }
        if (Theme.isClick(event.button())) {
            int i = (int) ((event.y() - y - 1) / rowH);
            if (i >= 0 && i < entries.size()) {
                Runnable action = entries.get(i).action();
                close();
                action.run();
            }
        }
        return true;
    }
}