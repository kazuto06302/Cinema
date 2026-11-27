package net.kztmc.mc.cinema.ui.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** スクロールできる右側のパネル(NPCの編集・ワールド設定)が、画面(CinemaScreen)に頼む機能 */
public interface ScrollPanel {
    boolean contains(double mx, double my);

    boolean mouseScrolled(double mx, double my, double scrollY);

    boolean wheelLocked(double mx, double my);

    boolean beginDragScroll(double mx, double my);

    boolean isDragScrolling();

    void dragScrollBy(double dy);

    void endDragScroll();

    /** スクロールする部品を、表示範囲で切り取って描く */
    void extractScrollItems(GuiGraphicsExtractor g, int mx, int my, float delta);

    /** スクロールの表示範囲の外(上の見出し・下の余白)か(パネルの中で) */
    boolean outsideScrollBand(double mx, double my);

    /** スクロールする部品の、押せる・押せないを、まとめて切り替える */
    void setScrollItemsActive(boolean on);
}