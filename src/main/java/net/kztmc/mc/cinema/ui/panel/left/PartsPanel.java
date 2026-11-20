package net.kztmc.mc.cinema.ui.panel.left;

import java.util.ArrayList;
import java.util.List;
import net.kztmc.mc.cinema.entity.ModelPartTree;
import net.kztmc.mc.cinema.entity.PartEdits;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.core.Layout;
import net.kztmc.mc.cinema.ui.core.PanelHost;
import net.kztmc.mc.cinema.ui.core.Tr;
import net.kztmc.mc.cinema.ui.panel.PartSelection;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 左パネル(パーツ一覧): 選択中のNPCのモデルのパーツを、全部並べる。クリックで選ぶ(右パネルで調整する)
public final class PartsPanel {
    private record Entry(String path, String label, int depth) {}

    private Layout lay;
    private PanelHost host;
    private ModernList<Entry> list;
    private double scroll;
    private int titleX, titleY;

    // 作り直す前に、リストのスクロール位置を保存する
    public void saveScroll() {
        if (list != null) scroll = list.getScroll();
    }

    public void build(Layout lay, PanelHost host, NpcData d) {
        this.lay = lay;
        this.host = host;
        list = null;

        int x = lay.leftX() + lay.pad();
        int w = lay.leftW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);
        int bottom = lay.panelY() + lay.panelH() - lay.pad();

        titleX = x;
        titleY = y;
        y += lay.th() + Theme.u(5);

        // 一覧(モデルのパーツの木 + 持っているアイテム)
        List<Entry> entries = new ArrayList<>();
        Entity live = NpcManager.entityOf(d.id());
        if (live != null) {
            for (ModelPartTree.Node n : ModelPartTree.of(live)) {
                entries.add(new Entry(n.path(), ModelPartTree.label(n.name()), n.depth()));
            }
            if (live instanceof LivingEntity) {
                for (String item : new String[] {PartEdits.ITEM_MAIN, PartEdits.ITEM_OFF}) {
                    entries.add(new Entry(item, ModelPartTree.label(item), 0));
                }
            }
        }

        int btnH = lay.btnH();
        list = new ModernList<>(x, y, w, bottom - y - btnH - lay.gap(), Tr.get("cinema.ui.parts.empty"),
                e -> "  ".repeat(e.depth()) + e.label(),                              // 木の深さのぶん、右へ
                e -> PartEdits.isEdited(d, e.path()) ? "●" : "",                       // 編集済みの印
                e -> e.path().equals(PartSelection.selected()),
                e -> {
                    PartSelection.select(e.path());
                    host.refresh();
                });
        list.setColor(e -> PartEdits.isEdited(d, e.path()) ? Theme.CHANGED : null);   // 編集済みは、黄色
        list.setItems(entries);
        list.setScroll(scroll);
        host.add(list);

        UUIDHolder.id = d.id();
        host.add(new ModernButton(x, bottom - btnH, w, btnH, Tr.get("cinema.ui.parts.reset_all"),
                ModernButton.Style.DEFAULT, () -> {
            NpcManager.get(UUIDHolder.id).ifPresent(cur -> NpcManager.replace(PartEdits.reset(cur, null)));
            host.refresh();
        }));
    }

    // パネルの背景と、見出し(ウィジェットの前に呼ぶ)
    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.leftX(), lay.panelY(), lay.leftW(), lay.panelH());
        Theme.text(g, host.font(), Tr.get("cinema.ui.parts.list"), titleX, titleY, Theme.ACCENT);
    }

    // ボタンの中から、NPC の ID を読むための入れ物
    private static final class UUIDHolder {
        static java.util.UUID id;
    }
}