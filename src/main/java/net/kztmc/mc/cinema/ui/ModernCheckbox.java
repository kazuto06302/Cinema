package net.kztmc.mc.cinema.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** チェックボックス。左にラベル、右に四角。オンのときは、四角の中に、塗りつぶした小さい四角が入る。 */
public class ModernCheckbox extends AbstractWidget {
    private final String label;
    private final Consumer<Boolean> onChange;
    private boolean checked;
    private float hover;

    public ModernCheckbox(int x, int y, int w, int h, String label, boolean checked, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.checked = checked;
        this.onChange = onChange;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = active && mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(),
                Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover));

        int pad = Theme.u(7);
        Theme.text(g, font, label, getX() + pad, getY() + (getHeight() - Theme.textH()) / 2, Theme.TEXT_DIM);

        // 右の四角: 外側の枠 + (オンのとき)中の塗りつぶし
        int size = Math.max(6, Math.min(getHeight() - Theme.u(6), Theme.u(12)));
        int bx = getX() + getWidth() - pad - size;
        int by = getY() + (getHeight() - size) / 2;
        g.outline(bx, by, size, size, checked ? Theme.ACCENT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hover));
        if (checked) {
            int inset = Math.max(2, size / 4);
            g.fill(bx + inset, by + inset, bx + size - inset, by + size - inset, Theme.ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        checked = !checked;
        onChange.accept(checked);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}