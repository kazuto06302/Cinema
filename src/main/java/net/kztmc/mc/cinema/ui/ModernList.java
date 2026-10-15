package net.kztmc.mc.cinema.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** スクロールできるリスト。NPC一覧とモブ選択の両方で使う。 */
public class ModernList<T> extends AbstractWidget {
    private final Function<T, String> label;
    private final Function<T, String> sub;
    private final Predicate<T> isSelected;
    private final Consumer<T> onSelect;
    private final String emptyText;

    private List<T> items = List.of();
    private double scroll;

    public ModernList(int x, int y, int w, int h, String emptyText,
                      Function<T, String> label, Function<T, String> sub,
                      Predicate<T> isSelected, Consumer<T> onSelect) {
        super(x, y, w, h, Component.empty());
        this.emptyText = emptyText;
        this.label = label;
        this.sub = sub;
        this.isSelected = isSelected;
        this.onSelect = onSelect;
    }

    /** 1行の高さ。文字サイズに連動 */
    private int rowH() {
        return Math.max(Theme.textH() + 3, Theme.u(14));
    }

    public void setItems(List<T> items) {
        this.items = items;
        this.scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    public double getScroll() {
        return scroll;
    }

    public void setScroll(double scroll) {
        this.scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int contentH() {
        return items.size() * rowH();
    }

    private double maxScroll() {
        return Math.max(0, contentH() - getHeight());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int rowH = rowH();
        int th = Theme.textH();

        Theme.roundRect(g, x, y, w, h, Theme.SURFACE);

        if (items.isEmpty()) {
            Theme.textCentered(g, font, emptyText, x + w / 2, y + (h - th) / 2, Theme.TEXT_DIM);
            return;
        }

        boolean overflow = maxScroll() > 0;
        int bar = Math.max(1, Theme.u(2));
        int padL = Theme.u(8);
        int textRight = x + w - (overflow ? bar + Theme.u(6) : Theme.u(6));

        g.enableScissor(x, y + 1, x + w, y + h - 1);
        boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
        for (int i = 0; i < items.size(); i++) {
            int ry = y + i * rowH - (int) scroll;
            if (ry + rowH < y || ry > y + h) continue;
            T item = items.get(i);
            boolean sel = isSelected.test(item);
            boolean hov = inside && my >= ry && my < ry + rowH;

            if (sel) {
                g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, 0xFF22406E);
                g.fill(x + 1, ry + 1, x + 1 + bar, ry + rowH - 1, Theme.ACCENT);
            } else if (hov) {
                g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, Theme.SURFACE_HOVER);
            }

            String subText = sub == null ? "" : sub.apply(item);
            int subW = subText.isEmpty() ? 0 : Theme.textWidth(font, subText) + Theme.u(6);
            String main = Theme.fit(font, label.apply(item), textRight - (x + padL) - subW);
            int ty = ry + (rowH - th) / 2;
            Theme.text(g, font, main, x + padL, ty, sel ? 0xFFFFFFFF : Theme.TEXT);
            if (!subText.isEmpty()) {
                Theme.textRight(g, font, subText, textRight, ty, Theme.TEXT_DIM);
            }
        }
        g.disableScissor();

        if (overflow) {
            int trackX = x + w - bar - Math.max(1, Theme.u(3));
            int thumbH = Math.max(Theme.u(12), (int) ((double) h * h / contentH()));
            int thumbY = y + 2 + (int) ((h - 4 - thumbH) * (scroll / maxScroll()));
            g.fill(trackX, y + 2, trackX + bar, y + h - 2, Theme.BORDER);
            g.fill(trackX, thumbY, trackX + bar, thumbY + thumbH, Theme.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        int index = (int) ((event.y() - getY() + scroll) / rowH());
        if (index >= 0 && index < items.size()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onSelect.accept(items.get(index));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!isMouseOver(mx, my) || maxScroll() <= 0) return false;
        scroll = Mth.clamp(scroll - scrollY * rowH(), 0, maxScroll());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}