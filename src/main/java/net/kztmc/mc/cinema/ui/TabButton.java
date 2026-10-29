package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 角丸のないタブ。選んでいるタブは、明るい背景と、下の線で示す。 */
public class TabButton extends AbstractWidget {
    private final String label;
    private final Runnable onClick;
    private boolean selected;
    private float hover;

    public TabButton(int x, int y, int w, int h, String label, Runnable onClick) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.onClick = onClick;
    }

    public TabButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = active && mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;

        int base = selected ? Theme.SURFACE_HOVER : Theme.SURFACE;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), Theme.lerp(base, Theme.SURFACE_HOVER, hover * 0.6f));
        if (selected) {
            g.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(), Theme.ACCENT);
        }

        String shown = Theme.fit(font, label, getWidth() - Theme.u(6));
        Theme.text(g, font, shown, getX() + (getWidth() - Theme.textWidth(font, shown)) / 2,
                getY() + (getHeight() - 2 - Theme.textH()) / 2, selected ? Theme.TEXT : Theme.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        onClick.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}