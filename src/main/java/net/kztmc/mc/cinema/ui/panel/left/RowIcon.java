package net.kztmc.mc.cinema.ui.panel.left;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** リストの行の、文字の前に出す、小さなアイコンの描画。 */
@FunctionalInterface
public interface RowIcon<T> {
    /** (x, y) から size × size の正方形に、item のアイコンを描く */
    void paint(GuiGraphicsExtractor g, T item, int x, int y, int size);
}