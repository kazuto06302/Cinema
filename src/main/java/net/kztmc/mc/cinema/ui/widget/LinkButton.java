package net.kztmc.mc.cinema.ui.widget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** リンクのボタン(オン: 輪がつながる / オフ: 離れる)。押すたびに、切り替わる。 */
public class LinkButton extends AbstractWidget {
    private final BooleanSupplier linked;
    private final Consumer<Boolean> onChange;
    private float hover;

    public LinkButton(int x, int y, int w, int h, BooleanSupplier linked, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.empty());
        this.linked = linked;
        this.onChange = onChange;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean hov = active && isMouseOver(mx, my);
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;
        boolean on = linked.getAsBoolean();
        if (hov) Tooltips.request(this, on ? "リンク中(3軸を、同じ割合でまとめて変える)" : "リンクなし(軸ごとに変える)");

        if (hover > 0.02f) {
            Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(),
                    Theme.lerp(Theme.GHOST_HOVER, Theme.SURFACE_HOVER, hover));
        }

        // 2つの輪。オンは重ねて、オフは離す
        int color = on ? Theme.ACCENT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hover);
        int s = Math.min(getWidth(), getHeight());
        int rw = Math.max(5, s * 5 / 9);
        int rh = Math.max(3, s * 2 / 5);
        int overlap = on ? rw / 3 : -Math.max(2, rw / 4);
        int left = getX() + (getWidth() - (rw * 2 - overlap)) / 2;
        int top = getY() + (getHeight() - rh) / 2;
        g.outline(left, top, rw, rh, color);
        g.outline(left + rw - overlap, top, rw, rh, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        UiSounds.click();
        onChange.accept(!linked.getAsBoolean());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}