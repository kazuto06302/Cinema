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

/** カード型のグリッドリスト。各カードは「画像+名前」。縦スクロール対応。 */
public class ModernCardGrid<T> extends AbstractWidget {
    private static final int CARD_BG = 0xFF1F2430;

    /** カードの画像を描く。x, y から size 四方に描くこと */
    public interface IconPainter<T> {
        void paint(GuiGraphicsExtractor g, T item, int x, int y, int size);
    }

    private final int cols;
    private final Function<T, String> label;
    private final IconPainter<T> painter;
    private final Consumer<T> onSelect;
    private final String emptyText;

    private List<T> items = List.of();
    private double scroll;

    private Predicate<T> highlight = t -> false;

    /** 条件に合うカードを枠で強調する(調整中のモブの表示用) */
    public void setHighlight(Predicate<T> highlight) {
        this.highlight = highlight;
    }

    public ModernCardGrid(int x, int y, int w, int h, int cols, String emptyText,
                          Function<T, String> label, IconPainter<T> painter, Consumer<T> onSelect) {
        super(x, y, w, h, Component.empty());
        this.cols = Math.max(1, cols);
        this.emptyText = emptyText;
        this.label = label;
        this.painter = painter;
        this.onSelect = onSelect;
    }

    // ---- 寸法(文字サイズに連動) ----

    private int gap() {
        return Math.max(1, Theme.u(4));
    }

    private int cardW() {
        return (getWidth() - gap() * (cols + 1)) / cols;
    }

    private int iconSize() {
        return Math.max(4, Math.min(Theme.u(40), cardW() - gap() * 2));
    }

    private float labelScale = 1.0f;

    /** カードの名前の文字の大きさ(UI標準を1.0とした倍率) */
    public ModernCardGrid<T> setLabelScale(float s) {
        this.labelScale = s;
        return this;
    }

    private float labelFontScale() {
        return Theme.fontScale() * labelScale;
    }

    private int labelH() {
        return Math.max(3, Math.round(8 * labelFontScale()));
    }

    private int cardH() {
        return gap() + iconSize() + Theme.u(3) + labelH() + gap();
    }

    private int rows() {
        return (items.size() + cols - 1) / cols;
    }

    private int contentH() {
        return gap() + rows() * (cardH() + gap());
    }

    private double maxScroll() {
        return Math.max(0, contentH() - getHeight());
    }

    // ---- API ----

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

    // ---- 描画 ----

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int gap = gap(), cw = cardW(), ch = cardH(), icon = iconSize();

        Theme.roundRect(g, x, y, w, h, Theme.SURFACE);

        if (items.isEmpty()) {
            Theme.textCentered(g, font, emptyText, x + w / 2, y + (h - Theme.textH()) / 2, Theme.TEXT_DIM);
            return;
        }

        g.enableScissor(x, y + 1, x + w, y + h - 1);
        boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
        for (int i = 0; i < items.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + gap + col * (cw + gap);
            int cy = y + gap + row * (ch + gap) - (int) scroll;
            if (cy + ch < y || cy > y + h) continue;   // 画面外のカードは描かない(エンティティ生成も走らない)

            T item = items.get(i);
            boolean hov = inside && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;

            boolean hl = highlight.test(item);
            Theme.roundRect(g, cx, cy, cw, ch, (hov || hl) ? Theme.SURFACE_HOVER : CARD_BG);
            if (hov || hl) g.outline(cx, cy, cw, ch, Theme.ACCENT);

            painter.paint(g, item, cx + (cw - icon) / 2, cy + gap, icon);

            float ls = labelFontScale();
            String name = Theme.fit(font, label.apply(item), cw - Theme.u(6), ls);
            Theme.textCentered(g, font, name, cx + cw / 2, cy + gap + icon + Theme.u(3),
                    hov ? 0xFFFFFFFF : Theme.TEXT, ls);
        }
        g.disableScissor();

        if (maxScroll() > 0) {
            int bar = Math.max(1, Theme.u(2));
            int trackX = x + w - bar - Math.max(1, Theme.u(2));
            int thumbH = Math.max(Theme.u(12), (int) ((double) h * h / contentH()));
            int thumbY = y + 2 + (int) ((h - 4 - thumbH) * (scroll / maxScroll()));
            g.fill(trackX, y + 2, trackX + bar, y + h - 2, Theme.BORDER);
            g.fill(trackX, thumbY, trackX + bar, thumbY + thumbH, Theme.ACCENT);
        }
    }

    // ---- 入力 ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        int gap = gap(), cw = cardW(), ch = cardH();

        double relX = event.x() - getX() - gap;
        double relY = event.y() - getY() - gap + scroll;
        if (relX < 0 || relY < 0) return true;

        int col = (int) (relX / (cw + gap));
        int row = (int) (relY / (ch + gap));
        boolean onCard = relX - col * (cw + gap) < cw && relY - row * (ch + gap) < ch;
        int index = row * cols + col;
        if (col < cols && onCard && index >= 0 && index < items.size()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onSelect.accept(items.get(index));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!isMouseOver(mx, my) || maxScroll() <= 0) return false;
        scroll = Mth.clamp(scroll - scrollY * (cardH() + gap()), 0, maxScroll());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}