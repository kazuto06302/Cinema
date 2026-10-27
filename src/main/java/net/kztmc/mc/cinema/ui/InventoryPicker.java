package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 自分のインベントリ(9列 × 3段 + ホットバー)。アイコンを押すと、そのアイテムを「つかむ」。
 * つかんだアイテムのドラッグと、スロットへの受け渡しは、画面(CinemaScreen)が行う。
 */
public class InventoryPicker extends AbstractWidget {
    public interface Pick {
        void on(ItemStack stack, double mouseX, double mouseY);
    }

    private static final int COLS = 9;
    private final int cell;
    private final int pad;
    private final int titleH;
    private final int rowGap;
    private final Pick onPick;

    public InventoryPicker(int x, int y, int cell, Pick onPick) {
        super(x, y, 0, 0, Component.empty());
        this.cell = cell;
        this.pad = Math.max(2, Theme.u(5));
        this.titleH = Theme.textH() + Theme.u(6);
        this.rowGap = Math.max(2, Theme.u(4));
        this.onPick = onPick;
        this.width = cell * COLS + pad * 2;
        this.height = titleH + cell * 4 + rowGap + pad;
    }

    /** 画面上の番号(0〜3段目 = 上から)を、インベントリの番号に直す。0〜2段目が 9〜35、3段目(ホットバー)が 0〜8 */
    private static int slotIndex(int row, int col) {
        return row < 3 ? 9 + row * COLS + col : col;
    }

    private int cellX(int col) {
        return getX() + pad + col * cell;
    }

    private int cellY(int row) {
        return getY() + titleH + row * cell + (row == 3 ? rowGap : 0);
    }

    private int[] cellAt(double mx, double my) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < COLS; col++) {
                if (mx >= cellX(col) && mx < cellX(col) + cell && my >= cellY(row) && my < cellY(row) + cell) {
                    return new int[] {row, col};
                }
            }
        }
        return null;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Font font = mc.font;

        Theme.panel(g, getX(), getY(), getWidth(), getHeight());
        int[] hovered = cellAt(mx, my);
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < COLS; col++) {
                int cx = cellX(col);
                int cy = cellY(row);
                ItemStack stack = player.getInventory().getItem(slotIndex(row, col));
                boolean hov = hovered != null && hovered[0] == row && hovered[1] == col;
                Theme.roundRect(g, cx + 1, cy + 1, cell - 2, cell - 2, hov ? Theme.SURFACE_HOVER : Theme.SURFACE);
                if (!stack.isEmpty()) {
                    int ix = cx + (cell - 16) / 2;
                    int iy = cy + (cell - 16) / 2;
                    g.item(stack, ix, iy);
                    g.itemDecorations(font, stack, ix, iy);
                    if (hov) hoveredStack = stack;
                }
            }
        }

        String title = hoveredStack.isEmpty() ? "インベントリ(ドラッグして装備へ)" : hoveredStack.getHoverName().getString();
        Theme.text(g, font, Theme.fit(font, title, getWidth() - pad * 2), getX() + pad,
                getY() + (titleH - Theme.textH()) / 2, hoveredStack.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) return false;
        if (!Theme.isClick(event.button())) return true;   // パネル内のクリックは、ほかへ渡さない
        LocalPlayer player = Minecraft.getInstance().player;
        int[] c = cellAt(event.x(), event.y());
        if (player != null && c != null) {
            ItemStack stack = player.getInventory().getItem(slotIndex(c[0], c[1]));
            if (!stack.isEmpty()) {
                onPick.on(stack.copy(), event.x(), event.y());
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}