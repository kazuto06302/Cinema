package net.kztmc.mc.cinema.ui.widget;

import java.util.function.IntSupplier;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** いまの色で塗った四角。押すと、onPress が呼ばれる(パレットの開閉に使う) */
public class ColorSwatch extends AbstractWidget {
    private final IntSupplier rgb;
    private final Runnable onPress;
    private float hover;

    public ColorSwatch(int x, int y, int w, int h, IntSupplier rgb, Runnable onPress) {
        super(x, y, w, h, Component.empty());
        this.rgb = rgb;
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean hov = active && isMouseOver(mx, my);
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF000000 | rgb.getAsInt());
        g.outline(getX(), getY(), getWidth(), getHeight(), Theme.lerp(Theme.BORDER, 0xFFFFFFFF, hover));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        UiSounds.click();
        onPress.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}