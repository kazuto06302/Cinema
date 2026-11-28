package net.kztmc.mc.cinema.ui.panel.left;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcGroups;
import net.kztmc.mc.cinema.ui.core.Draggable;
import net.kztmc.mc.cinema.ui.core.NpcNames;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * グループつきのNPC一覧。行は、グループの見出し(クリックで開閉)と、NPC(クリックで選択)。
 * NPCの行をドラッグして、グループの行(またはその中のNPC)の上で離すと、そのグループへ入る。グループの外で離すと、外へ出る。
 */
public class NpcTreeList extends AbstractWidget implements Draggable {
    public sealed interface Row permits GroupRow, NpcRow {}

    public record GroupRow(NpcGroups.Group group, int count) implements Row {}

    /** member が true なら、グループの中(字下げして出す) */
    public record NpcRow(NpcData data, boolean member) implements Row {}

    /** この距離(ドット)以内なら、ドラッグではなくクリックとして扱う */
    private static final double CLICK_SLOP = 4.0;

    private final String emptyText;
    private final Predicate<NpcData> isSelected;
    private final Consumer<NpcData> onSelectNpc;
    private final Consumer<NpcGroups.Group> onToggleGroup;
    private final BiConsumer<NpcData, NpcGroups.Group> onMove;   // group が null なら、グループの外へ
    private final Predicate<NpcGroups.Group> isGroupSelected;
    private final Consumer<NpcGroups.Group> onSelectGroup;
    private double pressX;

    private List<Row> rows = List.of();
    private double scroll;

    private boolean pressing;
    private boolean dragging;
    private double moved;
    private Row pressRow;

    private UUID editingGroup;   // 名前を編集中のグループ(その行は、名前を描かない。入力欄が重なる)
    private boolean scrollLocked;

    public NpcTreeList(int x, int y, int w, int h, String emptyText, Predicate<NpcData> isSelected,
                       Consumer<NpcData> onSelectNpc, Predicate<NpcGroups.Group> isGroupSelected,
                       Consumer<NpcGroups.Group> onSelectGroup, Consumer<NpcGroups.Group> onToggleGroup,
                       BiConsumer<NpcData, NpcGroups.Group> onMove) {
        super(x, y, w, h, Component.empty());
        this.emptyText = emptyText;
        this.isSelected = isSelected;
        this.onSelectNpc = onSelectNpc;
        this.isGroupSelected = isGroupSelected;
        this.onSelectGroup = onSelectGroup;
        this.onToggleGroup = onToggleGroup;
        this.onMove = onMove;
    }

    // ---- 寸法 ----

    private int rowH() {
        return Math.max(Theme.textH() + 3, Theme.u(14));
    }

    private double maxScroll() {
        return Math.max(0, rows.size() * rowH() - getHeight());
    }

    // ---- 外から使う ----

    public void setRows(List<Row> rows) {
        this.rows = rows;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    public double getScroll() {
        return scroll;
    }

    public void setScroll(double scroll) {
        this.scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** 名前の編集中は、行を動かさない(入力欄が、行の位置に重なっているため) */
    public void setEditingGroup(UUID id) {
        this.editingGroup = id;
        this.scrollLocked = id != null;
    }

    /** y の位置の行。なければ null */
    public Row rowAt(double my) {
        if (my < getY() || my >= getY() + getHeight()) return null;
        int index = (int) ((my - getY() + scroll) / rowH());
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private int indexOfGroup(UUID id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof GroupRow gr && gr.group().id().equals(id)) return i;
        }
        return -1;
    }

    /** グループの行を、見える位置までスクロールする */
    public void scrollTo(UUID groupId) {
        int i = indexOfGroup(groupId);
        if (i < 0) return;
        double top = (double) i * rowH();
        double bottom = top + rowH();
        if (top < scroll) scroll = top;
        else if (bottom > scroll + getHeight()) scroll = bottom - getHeight();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** グループの行の、{ x, y, w, h }。画面に完全に見えていなければ null(名前の入力欄を重ねる位置) */
    public int[] rectOfGroup(UUID groupId) {
        int i = indexOfGroup(groupId);
        if (i < 0) return null;
        int ry = getY() + i * rowH() - (int) scroll;
        if (ry < getY() || ry + rowH() > getY() + getHeight()) return null;
        return new int[] {getX() + 1, ry, getWidth() - 2, rowH()};
    }

    // ---- 描画 ----

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int rowH = rowH();
        int th = Theme.textH();

        Theme.roundRect(g, x, y, w, h, Theme.SURFACE);
        if (rows.isEmpty()) {
            Theme.textCentered(g, font, emptyText, x + w / 2, y + (h - th) / 2, Theme.TEXT_DIM);
            return;
        }

        if (dragging) autoScroll(my);

        boolean overflow = maxScroll() > 0;
        int bar = Math.max(1, Theme.u(2));
        int padL = Theme.u(8);
        int textRight = x + w - (overflow ? bar + Theme.u(6) : Theme.u(6));
        boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
        NpcGroups.Group dropGroup = dragging && inside ? dropGroupAt(my) : null;

        g.enableScissor(x, y + 1, x + w, y + h - 1);
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + i * rowH - (int) scroll;
            if (ry + rowH < y || ry > y + h) continue;
            Row row = rows.get(i);
            boolean hov = inside && !dragging && my >= ry && my < ry + rowH;
            if (row instanceof GroupRow gr) {
                boolean drop = dropGroup != null && dropGroup.id().equals(gr.group().id());
                drawGroup(g, font, gr, ry, padL, textRight, hov, drop);
            } else if (row instanceof NpcRow nr) {
                drawNpc(g, font, nr, ry, padL, textRight, hov, bar);
            }
        }
        g.disableScissor();

        if (overflow) {
            int trackX = x + w - bar - Math.max(1, Theme.u(3));
            int thumbH = Math.max(Theme.u(12), (int) ((double) h * h / (rows.size() * rowH)));
            int thumbY = y + 2 + (int) ((h - 4 - thumbH) * (scroll / maxScroll()));
            g.fill(trackX, y + 2, trackX + bar, y + h - 2, Theme.BORDER);
            g.fill(trackX, thumbY, trackX + bar, thumbY + thumbH, Theme.ACCENT);
        }

        if (dragging && pressRow instanceof NpcRow nr) drawGhost(g, font, nr.data(), mx, my);
    }

    private void drawGroup(GuiGraphicsExtractor g, Font font, GroupRow gr, int ry, int padL, int textRight,
                           boolean hov, boolean drop) {
        int x = getX(), w = getWidth(), rowH = rowH(), th = Theme.textH();
        int bar = Math.max(1, Theme.u(2));
        boolean sel = isGroupSelected.test(gr.group());
        if (drop) {
            g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, Theme.SELECTED);
            g.outline(x + 1, ry + 1, w - 2, rowH - 2, Theme.ACCENT);
        } else if (sel) {
            g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, Theme.SELECTED);
            g.fill(x + 1, ry + 1, x + 1 + bar, ry + rowH - 1, Theme.ACCENT);
        } else if (hov) {
            g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, Theme.SURFACE_HOVER);
        }

        int s = Math.max(3, Theme.u(3));
        triangle(g, x + padL + s, ry + rowH / 2, s, gr.group().isOpen(), Theme.SECTION);
        int tx = x + padL + s * 2 + Theme.u(5);
        int ty = ry + (rowH - th) / 2;

        String count = String.valueOf(gr.count());
        int countW = Theme.textWidth(font, count) + Theme.u(6);
        if (!gr.group().id().equals(editingGroup)) {
            Theme.text(g, font, Theme.fit(font, gr.group().displayName(), textRight - tx - countW), tx, ty,
                    sel ? 0xFFFFFFFF : Theme.SECTION);
        }
        Theme.textRight(g, font, count, textRight, ty, Theme.TEXT_DIM);
    }

    private void drawNpc(GuiGraphicsExtractor g, Font font, NpcRow nr, int ry, int padL, int textRight,
                         boolean hov, int bar) {
        int x = getX(), w = getWidth(), rowH = rowH(), th = Theme.textH();
        NpcData d = nr.data();
        boolean sel = isSelected.test(d);
        if (sel) {
            g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, Theme.SELECTED);
            g.fill(x + 1, ry + 1, x + 1 + bar, ry + rowH - 1, Theme.ACCENT);
        } else if (hov) {
            g.fill(x + 1, ry + 1, x + w - 1, ry + rowH - 1, Theme.SURFACE_HOVER);
        }

        int indent = nr.member() ? Theme.u(12) : 0;
        if (nr.member()) {   // グループの中: 左に、つながりの線
            int gx = x + padL + Theme.u(3);
            g.fill(gx, ry, gx + 1, ry + rowH, Theme.SECTION_LINE);
        }

        int size = rowH - Theme.u(4);
        int textX = x + padL + indent;
        NpcFaces.paint(g, d, textX - Theme.u(3), ry + (rowH - size) / 2, size);
        textX += size + Theme.u(3);

        String sub = NpcData.PLAYER.equals(d.kind()) ? "Player" : "Mob";
        int subW = Theme.textWidth(font, sub) + Theme.u(6);
        int ty = ry + (rowH - th) / 2;
        boolean ghosted = dragging && pressRow == nr;   // ドラッグ中の行は、薄くする
        int color = ghosted ? Theme.TEXT_DIM : (sel ? 0xFFFFFFFF : Theme.TEXT);
        Theme.text(g, font, Theme.fit(font, NpcNames.of(d), textRight - textX - subW), textX, ty, color);
        Theme.textRight(g, font, sub, textRight, ty, Theme.TEXT_DIM);
    }

    /** ドラッグ中、カーソルについてくる名前 */
    private void drawGhost(GuiGraphicsExtractor g, Font font, NpcData d, int mx, int my) {
        String name = NpcNames.of(d);
        int pad = Theme.u(5);
        int gw = Theme.textWidth(font, name) + pad * 2;
        int gh = Theme.textH() + pad * 2;
        int gx = mx + Theme.u(10);
        int gy = my + Theme.u(6);
        g.nextStratum();   // ほかの部品より、手前に描く
        g.fill(gx, gy, gx + gw, gy + gh, Theme.POPUP);
        g.outline(gx, gy, gw, gh, Theme.ACCENT);
        Theme.text(g, font, name, gx + pad, gy + pad, Theme.TEXT);
    }

    /** 小さな三角(open が true なら▼、false なら▶) */
    private static void triangle(GuiGraphicsExtractor g, int cx, int cy, int s, boolean open, int color) {
        for (int i = 0; i < s; i++) {
            int half = s - i;
            if (open) {
                g.fill(cx - half + 1, cy - s / 2 + i, cx + half, cy - s / 2 + i + 1, color);
            } else {
                g.fill(cx - s / 2 + i, cy - half + 1, cx - s / 2 + i + 1, cy + half, color);
            }
        }
    }

    /** ドラッグ中、上下の端に近づいたら、一覧をスクロールする */
    private void autoScroll(int my) {
        int edge = rowH() / 2 + Theme.u(4);
        if (my < getY() + edge) scroll = Mth.clamp(scroll - 3, 0, maxScroll());
        else if (my > getY() + getHeight() - edge) scroll = Mth.clamp(scroll + 3, 0, maxScroll());
    }

    // ---- 入力 ----

    /** その位置で離したときに、入るグループ。グループの行・その中のNPCなら、そのグループ。それ以外は null(外へ出す) */
    private NpcGroups.Group dropGroupAt(double my) {
        Row row = rowAt(my);
        if (row instanceof GroupRow gr) return gr.group();
        if (row instanceof NpcRow nr) return NpcGroups.groupOf(nr.data().id());
        return null;
    }

    /** ▼(▶)を押したと見なす、右端のx。描画の矢印と同じ位置から計算する */
    private int arrowZoneRight() {
        int s = Math.max(3, Theme.u(3));
        return getX() + Theme.u(8) + s * 2 + Theme.u(4);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        pressing = true;
        dragging = false;
        moved = 0;
        pressX = event.x();   // 追加
        pressRow = rowAt(event.y());
        return true;
    }

    @Override
    public void dragBy(double dx, double dy) {
        if (!pressing) return;
        moved += Math.abs(dx) + Math.abs(dy);
        if (moved > CLICK_SLOP && pressRow instanceof NpcRow) dragging = true;   // ドラッグできるのは、NPCの行だけ
    }

    @Override
    public void dragEnd(double mouseX, double mouseY) {
        if (!pressing) return;
        boolean wasDragging = dragging;
        Row row = pressRow;
        pressing = false;
        dragging = false;
        pressRow = null;

        if (wasDragging) {
            // 一覧の外で離したときは、何もしない
            if (row instanceof NpcRow nr && isMouseOver(mouseX, mouseY)) {
                onMove.accept(nr.data(), dropGroupAt(mouseY));
            }
            return;
        }
        if (moved > CLICK_SLOP) return;   // グループの行をドラッグした: クリックとは扱わない
        if (row instanceof NpcRow nr) {
            UiSounds.click();
            onSelectNpc.accept(nr.data());
        } else if (row instanceof GroupRow gr) {
            UiSounds.click();
            if (pressX < arrowZoneRight()) onToggleGroup.accept(gr.group());   // ▼ を押したときだけ、開閉する
            else onSelectGroup.accept(gr.group());                              // それ以外は、グループを選ぶ
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollLocked || !isMouseOver(mx, my) || maxScroll() <= 0) return false;
        scroll = Mth.clamp(scroll - scrollY * rowH(), 0, maxScroll());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}