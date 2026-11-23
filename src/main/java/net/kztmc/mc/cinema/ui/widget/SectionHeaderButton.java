package net.kztmc.mc.cinema.ui.widget;

import java.util.function.BooleanSupplier;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** クリックで畳める見出し(▼ 開いている / ▶ 畳んでいる)。右に線を引く。 */
public class SectionHeaderButton extends AbstractWidget {
    private final String text;
    private final BooleanSupplier collapsed;
    private final Runnable onToggle;
    private float hover;

    public SectionHeaderButton(int x, int y, int w, int h, String text, BooleanSupplier collapsed, Runnable onToggle) {
        super(x, y, w, h, Component.literal(text));
        this.text = text;
        this.collapsed = collapsed;
        this.onToggle = onToggle;
    }

    /** 見出しの高さ */
    public static int height() {
        return Theme.textH() + Theme.u(3);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = active && isMouseOver(mx, my);
        hover += ((hov ? 1f : 0f) - hover) * 0.35f;
        int color = Theme.lerp(Theme.SECTION, 0xFFFFFFFF, hover * 0.5f);

        int top = getY();
        int headH = Math.round(Theme.textH() * Theme.HEADING_SCALE);
        int s = Math.max(3, Theme.u(4));
        triangle(g, getX() + s, top + headH / 2, s, !collapsed.getAsBoolean(), color);

        int tx = getX() + s * 2 + Theme.u(4);
        String shown = Theme.fit(font, text, Math.round((getWidth() - (tx - getX())) / Theme.HEADING_SCALE));
        Theme.heading(g, font, shown, tx, top, color);

        int lineX = tx + Theme.headingWidth(font, shown) + Theme.u(6);
        int lineEnd = getX() + getWidth();
        if (lineEnd - lineX > Theme.u(12)) {
            int lineY = top + headH / 2;
            g.fill(lineX, lineY, lineEnd, lineY + 1, Theme.SECTION_LINE);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        UiSounds.tab();
        onToggle.run();
        return true;
    }

    private static void triangle(GuiGraphicsExtractor g, int cx, int cy, int s, boolean down, int color) {
        for (int i = 0; i < s; i++) {
            int half = s - i;
            if (down) {
                g.fill(cx - half + 1, cy - s / 2 + i, cx + half, cy - s / 2 + i + 1, color);
            } else {
                g.fill(cx - s / 2 + i, cy - half + 1, cx - s / 2 + i + 1, cy + half, color);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}