package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * アイテムのアイコンを表示するボタン。空のときは、見本のアイテム(ghost)を、薄く表示する。
 * 左クリックで onClick(0)、右クリックで onClick(1) を呼ぶ。
 */
public class ItemButton extends AbstractWidget {
    public interface Click {
        /** @param button 0 = 左クリック、1 = 右クリック */
        void on(int button);
    }

    private final ItemStack stack;
    private final ItemStack ghost;
    private final Click onClick;
    private boolean selected;
    private float hover;

    public ItemButton(int x, int y, int w, int h, ItemStack stack, ItemStack ghost, Click onClick) {
        super(x, y, w, h, Component.empty());
        this.stack = stack;
        this.ghost = ghost;
        this.onClick = onClick;
    }

    public ItemButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public ItemButton tip(String text) {
        setTooltip(Tooltip.create(Component.literal(text)));
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean hov = active && mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(), Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover));
        if (selected) {
            g.outline(getX(), getY(), getWidth(), getHeight(), Theme.ACCENT);
        }

        int ix = getX() + (getWidth() - 16) / 2;
        int iy = getY() + (getHeight() - 16) / 2;
        if (!stack.isEmpty()) {
            g.item(stack, ix, iy);
            g.itemDecorations(Minecraft.getInstance().font, stack, ix, iy);
        } else if (!ghost.isEmpty()) {
            g.item(ghost, ix, iy);
            g.fill(ix, iy, ix + 16, iy + 16, 0xB8191D26);   // 空のスロットの見本は、薄暗くする
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) return false;
        int b = event.button();
        if (Theme.isClick(b)) {
            onClick.on(0);
        } else if (Theme.isRightClick(b)) {
            onClick.on(1);
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}