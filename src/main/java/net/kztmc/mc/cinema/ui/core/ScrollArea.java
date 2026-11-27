package net.kztmc.mc.cinema.ui.core;

import java.util.ArrayList;
import java.util.List;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 縦にスクロールする領域。部品は add で足して、画面の描画では、表示範囲(scissor)で切り取って描く。
 * 使い方: begin → setBand → add(部品を、スクロール前の座標で) → end。スクロール位置は、作り直しても保持する。
 */
public final class ScrollArea implements ScrollPanel {
    private static final class Item {
        final AbstractWidget widget;
        final int baseY;

        Item(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private PanelHost host;
    private int panelX, panelY, panelW, panelH;   // パネル全体(切り取りの横幅と、パネルの中かの判定)
    private int top, bottom;                      // スクロールする部分の、表示範囲(上下)
    private int scroll, maxScroll;
    private boolean dragScrolling;
    private long wheelLockUntil;

    /** 作り直すたびに、最初に呼ぶ。部品の一覧は空になるが、スクロール位置は残る */
    public void begin(PanelHost host, int panelX, int panelY, int panelW, int panelH) {
        this.host = host;
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        items.clear();
        top = panelY;
        bottom = panelY;
        maxScroll = 0;
    }

    /** スクロールする部分の、表示範囲(上下) */
    public void setBand(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    public int top() { return top; }

    public int bottom() { return bottom; }

    /** いまのスクロール量(背景やラベルを、同じだけずらして描くとき用) */
    public int offset() { return scroll; }

    /** スクロールする部品として登録する(描画は、extractScrollItems が、切り取りながら行う) */
    public <T extends AbstractWidget> T add(T widget) {
        host.addChild(widget);
        items.add(new Item(widget, widget.getY()));
        return widget;
    }

    /**
     * 部品をすべて足したあとに呼ぶ。
     * @param contentEnd 中身の下端(スクロール前の座標)
     * @param barX       スクロールバーの左端
     */
    public void end(int contentEnd, int barX, int barW) {
        maxScroll = Math.max(0, contentEnd - bottom);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        host.add(new ScrollBar(barX, top, barW, bottom - top));
        apply();
    }

    private void apply() {
        for (Item it : items) {
            AbstractWidget widget = it.widget;
            widget.setY(it.baseY - scroll);
            // 表示範囲に、少しでも掛かっていれば表示する(はみ出した部分は、描くときに切り取る)
            boolean show = widget.getY() + widget.getHeight() > top && widget.getY() < bottom;
            widget.visible = show;
            widget.active = show;
        }
    }

    private void setScroll(int value) {
        scroll = Mth.clamp(value, 0, maxScroll);
        apply();
    }

    // ---- ScrollPanel ----

    @Override
    public boolean contains(double mx, double my) {
        return mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!contains(mx, my) || maxScroll <= 0) return false;
        setScroll(scroll - (int) Math.round(scrollY * Theme.u(24)));
        wheelLockUntil = System.currentTimeMillis() + 400;
        return true;
    }

    @Override
    public boolean wheelLocked(double mx, double my) {
        return System.currentTimeMillis() < wheelLockUntil && contains(mx, my);
    }

    @Override
    public boolean beginDragScroll(double mx, double my) {
        if (maxScroll <= 0 || mx < panelX || mx >= panelX + panelW || my < top || my >= bottom) return false;
        dragScrolling = true;
        return true;
    }

    @Override
    public boolean isDragScrolling() {
        return dragScrolling;
    }

    @Override
    public void dragScrollBy(double dy) {
        setScroll(scroll - (int) Math.round(dy));
        wheelLockUntil = System.currentTimeMillis() + 400;
    }

    @Override
    public void endDragScroll() {
        dragScrolling = false;
    }

    @Override
    public void extractScrollItems(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (items.isEmpty()) return;
        // 範囲の外に、カーソルがあるときは、ホバーの表示を出さない
        int hoverY = my >= top && my < bottom ? my : -10000;
        g.enableScissor(panelX, top, panelX + panelW, bottom);
        for (Item it : items) {
            if (it.widget.visible) it.widget.extractRenderState(g, mx, hoverY, delta);
        }
        g.disableScissor();
    }

    @Override
    public boolean outsideScrollBand(double mx, double my) {
        return contains(mx, my) && (my < top || my >= bottom);
    }

    @Override
    public void setScrollItemsActive(boolean on) {
        for (Item it : items) it.widget.active = on && it.widget.visible;
    }

    // ---- 右端のスクロールバー(つまみをドラッグできる) ----

    private final class ScrollBar extends AbstractWidget implements Draggable {
        ScrollBar(int bx, int by, int bw, int bh) {
            super(bx, by, bw, bh, Component.empty());
        }

        private int thumbH() {
            int view = bottom - top;
            int total = view + maxScroll;
            return Math.max(Theme.u(16), getHeight() * view / Math.max(1, total));
        }

        private int thumbY() {
            int free = getHeight() - thumbH();
            return getY() + (maxScroll == 0 ? 0 : free * scroll / maxScroll);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            if (maxScroll <= 0) return;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
            g.fill(getX(), thumbY(), getX() + getWidth(), thumbY() + thumbH(), 0x88FFFFFF);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return maxScroll > 0 && Theme.isClick(event.button()) && isMouseOver(event.x(), event.y());
        }

        @Override
        public void dragBy(double dx, double dy) {
            int free = getHeight() - thumbH();
            if (free > 0) setScroll(scroll + (int) Math.round(dy * maxScroll / free));
        }

        @Override
        public void dragEnd(double mouseX, double mouseY) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
}