package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 自前ボタン。ホバーでなめらかに色が変わる。 */
public class ModernButton extends AbstractWidget {
    public enum Style { PRIMARY, DEFAULT, GHOST, DANGER }

    private final Style style;
    private final Runnable onPress;
    private boolean selected;
    private float hover; // 0..1 のアニメーション値

    public ModernButton(int x, int y, int w, int h, String label, Style style, Runnable onPress) {
        super(x, y, w, h, Component.literal(label));
        this.style = style;
        this.onPress = onPress;
    }

    public ModernButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = this.active && mx >= getX() && mx < getX() + getWidth()
                && my >= getY() && my < getY() + getHeight();
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;

        int base, hot, textBase, textHot;
        switch (style) {
            case PRIMARY -> { base = Theme.ACCENT_DIM; hot = Theme.ACCENT; textBase = Theme.TEXT; textHot = 0xFFFFFFFF; }
            case DANGER -> { base = Theme.DANGER_DIM; hot = Theme.DANGER; textBase = 0xFFFF9AA0; textHot = 0xFFFFFFFF; }
            case GHOST -> { base = Theme.GHOST_HOVER; hot = Theme.SURFACE_HOVER; textBase = Theme.TEXT_DIM; textHot = Theme.TEXT; }
            default -> { base = Theme.SURFACE; hot = Theme.SURFACE_HOVER; textBase = Theme.TEXT; textHot = 0xFFFFFFFF; }
        }
        if (selected && style != Style.GHOST) {
            base = Theme.ACCENT_DIM;
            hot = Theme.ACCENT;
            textBase = 0xFFFFFFFF;
        }

        int bg = Theme.lerp(base, hot, hover);
        int fg = Theme.lerp(textBase, textHot, hover);
        if (selected && style == Style.GHOST) fg = Theme.ACCENT;

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(), bg);
        if (selected && style == Style.GHOST) {
            // 下線でタブの選択状態を表す
            int line = Math.max(1, Theme.u(2));
            int inset = Theme.u(4);
            g.fill(getX() + inset, getY() + getHeight() - line, getX() + getWidth() - inset, getY() + getHeight(), Theme.ACCENT);
        }

        String label = Theme.fit(font, getMessage().getString(), getWidth() - Theme.u(5));
        Theme.textCentered(g, font, label, getX() + getWidth() / 2, getY() + (getHeight() - Theme.textH()) / 2, fg);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}