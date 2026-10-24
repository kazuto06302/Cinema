package net.kztmc.mc.cinema.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * ドロップダウン(閉じているときは「ラベル  選択中の名前 ▼」)。クリックで、下(入らなければ上)に選択肢が開く。
 * 開いた選択肢は、他のウィジェットの上に描く必要があるので、画面(CinemaScreen)が、全部の描画のあとで extractPopup を呼ぶ。
 */
public class ModernDropdown extends AbstractWidget {
    private final String label;
    private final List<String> names;
    private final IntConsumer onSelect;
    private final Consumer<ModernDropdown> onOpen;
    private int selected;
    private boolean open;
    private float hover;

    public ModernDropdown(int x, int y, int w, int h, String label, List<String> names, int selected,
                          IntConsumer onSelect, Consumer<ModernDropdown> onOpen) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.names = names;
        this.selected = Math.max(0, Math.min(names.size() - 1, selected));
        this.onSelect = onSelect;
        this.onOpen = onOpen;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    // ---- 閉じているときの本体 ----

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = active && mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        hover += ((hov || open ? 1f : 0f) - hover) * 0.35f;

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(),
                Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover));

        int pad = Theme.u(7);
        int arrow = Theme.u(8);
        int ty = getY() + (getHeight() - Theme.textH()) / 2;

        Theme.text(g, font, label, getX() + pad, ty, Theme.TEXT_DIM);
        int room = getWidth() - pad * 2 - arrow - Theme.textWidth(font, label) - Theme.u(6);
        Theme.textRight(g, font, Theme.fit(font, names.get(selected), room), getX() + getWidth() - pad - arrow, ty, Theme.TEXT);
        drawArrow(g, getX() + getWidth() - pad - arrow / 2, getY() + getHeight() / 2, open, Theme.TEXT);
    }

    /** 小さな三角(閉じているときは下向き、開いているときは上向き) */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, boolean up, int color) {
        int s = Math.max(2, Theme.u(3));
        for (int i = 0; i < s; i++) {
            int half = up ? i + 1 : s - i;
            int y = cy - s / 2 + i;
            g.fill(cx - half + 1, y, cx + half, y + 1, color);
        }
    }

    // ---- 開いた選択肢(画面が、最後に描く) ----

    private int popupHeight() {
        return getHeight() * names.size() + 2;
    }

    private int popupY() {
        int below = getY() + getHeight() + 1;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return below + popupHeight() > screenH ? getY() - popupHeight() - 1 : below;
    }

    public void extractPopup(GuiGraphicsExtractor g, int mx, int my) {
        if (!open) return;
        Font font = Minecraft.getInstance().font;
        int px = getX(), py = popupY(), pw = getWidth(), ph = popupHeight();

        Theme.roundRect(g, px - 1, py - 1, pw + 2, ph + 2, Theme.BORDER);
        Theme.roundRect(g, px, py, pw, ph, Theme.SURFACE);

        for (int i = 0; i < names.size(); i++) {
            int ry = py + 1 + i * getHeight();
            boolean hov = mx >= px && mx < px + pw && my >= ry && my < ry + getHeight();
            if (i == selected) {
                g.fill(px + 1, ry, px + pw - 1, ry + getHeight(), 0xFF22406E);
                g.fill(px + 1, ry, px + 1 + Math.max(1, Theme.u(2)), ry + getHeight(), Theme.ACCENT);
            } else if (hov) {
                g.fill(px + 1, ry, px + pw - 1, ry + getHeight(), Theme.SURFACE_HOVER);
            }
            Theme.text(g, font, Theme.fit(font, names.get(i), pw - Theme.u(16)), px + Theme.u(8),
                    ry + (getHeight() - Theme.textH()) / 2, i == selected ? 0xFFFFFFFF : Theme.TEXT);
        }
    }

    /** 開いた選択肢の中のクリックなら、選んで閉じて true を返す。外なら false(閉じるかどうかは、画面が決める) */
    public boolean popupClicked(MouseButtonEvent event) {
        if (!open || !Theme.isClick(event.button())) return false;
        int px = getX(), py = popupY(), pw = getWidth();
        double mx = event.x(), my = event.y();
        if (mx < px || mx >= px + pw || my < py || my >= py + popupHeight()) return false;

        int i = (int) ((my - py - 1) / getHeight());
        if (i >= 0 && i < names.size()) {
            selected = i;
            onSelect.accept(i);
        }
        open = false;
        return true;
    }

    // ---- 本体のクリック: 開閉の切り替え ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        open = !open;
        if (open) onOpen.accept(this);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}