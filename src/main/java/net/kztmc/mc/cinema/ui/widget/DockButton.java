package net.kztmc.mc.cinema.ui.widget;

import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 左側に並べる、正方形のボタン。背景は描かず(パネルの背景の上に載る)、押せる場所を、ホバーで見せる */
public class DockButton extends AbstractWidget {
    /** アイコンの描き方(x, y は左上、size は一辺) */
    @FunctionalInterface
    public interface Icon {
        void paint(GuiGraphicsExtractor g, int x, int y, int size, int color);
    }

    /** ≡ (メニュー) */
    public static final Icon MENU = (g, x, y, s, c) -> {
        int w = s / 2;
        int h = Math.max(2, s / 12);
        int gap = Math.max(2, s / 6);
        int bx = x + (s - w) / 2;
        int by = y + (s - (h * 3 + gap * 2)) / 2;
        for (int i = 0; i < 3; i++) g.fill(bx, by + i * (h + gap), bx + w, by + i * (h + gap) + h, c);
    };

    /** — (小さくする) */
    public static final Icon MINIMIZE = (g, x, y, s, c) -> {
        int w = s / 2;
        int h = Math.max(2, s / 10);
        g.fill(x + (s - w) / 2, y + (s - h) / 2, x + (s + w) / 2, y + (s + h) / 2, c);
    };

    private final Icon icon;
    private final Runnable onPress;
    private float hover;

    public DockButton(int x, int y, int size, Icon icon, String tooltip, Runnable onPress) {
        super(x, y, size, size, Component.literal(tooltip));
        this.icon = icon;
        this.onPress = onPress;
        setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean hov = active && isMouseOver(mx, my);
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;
        if (hover > 0.02f) {
            Theme.roundRect(g, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2,
                    Theme.lerp(Theme.GHOST_HOVER, Theme.SURFACE_HOVER, hover));
        }
        icon.paint(g, getX(), getY(), getWidth(), Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hover));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        onPress.run();
        UiSounds.click();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}