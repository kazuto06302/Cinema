package net.kztmc.mc.cinema.ui.panel.right;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kztmc.mc.cinema.npc.NpcGroups;
import net.kztmc.mc.cinema.ui.core.Layout;
import net.kztmc.mc.cinema.ui.core.PanelHost;
import net.kztmc.mc.cinema.ui.core.Tr;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernTextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 右パネル: 選択中のグループ。
 *   最上段: 名前と削除(モブと同じ)
 *   基本  : 名前の入力欄(入力は、その場で反映する)
 *   モード: Normal / Ride / Fill(選べるだけで、動作は未実装)
 */
public final class GroupPanel {
    private record Label(String text, int x, int y, int color) {}

    private final List<Label> labels = new ArrayList<>();
    private final List<int[]> cards = new ArrayList<>();   // 各セクションの上と下
    private Layout lay;
    private PanelHost host;
    private UUID id;
    private int x, w;
    private int iconX, iconY, iconSize, nameX, nameY, nameMaxW;
    private int backX, backY, backW, backH;

    public void build(Layout lay, PanelHost host, NpcGroups.Group group) {
        this.lay = lay;
        this.host = host;
        this.id = group.id();
        UUID groupId = group.id();
        labels.clear();
        cards.clear();

        x = lay.rightX() + lay.pad();
        w = lay.panelW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);

        // 最上段: アイコンと名前(左)、削除(右)
        int delW = Theme.u(44);
        iconSize = lay.btnH();
        iconX = x;
        iconY = y;
        nameX = x + iconSize + lay.gap();
        nameY = y + (lay.btnH() - lay.th()) / 2;
        nameMaxW = w - delW - iconSize - lay.gap() * 2;
        host.add(new ModernButton(x + w - delW, y, delW, lay.btnH(), Tr.get("cinema.ui.delete"),
                ModernButton.Style.DANGER, () -> host.confirmDeleteGroup(groupId)));

        // ← (戻る): 右パネルの左の外に出す(選択を外して、ワールド設定へ戻る)
        backW = Theme.u(26);
        backH = lay.btnH() + Theme.u(4);
        backX = lay.rightX() - backW - lay.gap();
        backY = lay.panelY();
        host.add(new ModernButton(backX, backY, backW, backH, "←", ModernButton.Style.DEFAULT, () -> {
            NpcGroups.select(null);
            host.refresh();
        }));

        y += lay.btnH() + Theme.u(5);

        int indent = Theme.u(4);
        int cx = x + indent;
        int cw = w - indent;
        int gap = Math.max(1, Theme.u(3));
        int sectionGap = Theme.u(9);

        // --- 基本: 名前 ---
        int top = y;
        labels.add(new Label("基本", x, y, Theme.SECTION));
        y += lay.th() + Theme.u(3);

        ModernTextField nameBox = new ModernTextField(cx, y, cw, lay.btnH());
        nameBox.setHint("グループ名");
        nameBox.setMaxLength(32);
        nameBox.setValue(group.name());
        nameBox.setOnChange(s -> NpcGroups.rename(groupId, s));   // 左の一覧は、名前を直接読むので、すぐ変わる
        nameBox.setOnEnter(s -> {
            if (s.isBlank()) {
                NpcGroups.rename(groupId, "グループ");
                host.refresh();
            }
        });
        host.add(nameBox);
        y += lay.btnH();
        cards.add(new int[] {top, y});
        y += sectionGap;

        // --- モード: Normal / Ride / Fill ---
        top = y;
        labels.add(new Label("モード", x, y, Theme.SECTION));
        y += lay.th() + Theme.u(3);

        String[] names = {"Normal", "Ride", "Fill"};
        NpcGroups.Mode[] modes = NpcGroups.Mode.values();
        int bw = (cw - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            NpcGroups.Mode mode = modes[i];
            host.add(new ModernButton(cx + i * (bw + gap), y, bw, lay.btnH(), names[i],
                    ModernButton.Style.DEFAULT, () -> {
                NpcGroups.setMode(groupId, mode);
                host.refresh();
            }).setSelected(group.mode() == mode));
        }
        y += lay.btnH();
        cards.add(new int[] {top, y});
    }

    /** パネルの背景と、文字(ウィジェットの前に呼ぶ) */
    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        Theme.panel(g, backX, backY, backW, backH);   // 戻るボタンの背景

        drawFolder(g, iconX, iconY, iconSize, Theme.SECTION);
        NpcGroups.Group group = NpcGroups.find(id);
        if (group != null) {   // 入力中の名前が、すぐ変わるように、毎回読む
            Theme.text(g, host.font(), Theme.fit(host.font(), group.displayName(), nameMaxW), nameX, nameY, Theme.TEXT);
        }

        int padV = Theme.u(2);
        int padH = Theme.u(3);
        for (int[] span : cards) {
            Theme.roundRect(g, x - padH, span[0] - padV, w + padH * 2, span[1] - span[0] + padV, Theme.SECTION_CARD);
        }
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
    }

    /** 小さなフォルダ */
    private static void drawFolder(GuiGraphicsExtractor g, int x, int y, int s, int color) {
        int tabW = s * 2 / 5;
        int tabH = Math.max(2, s / 6);
        int bodyY = y + s / 4;
        g.fill(x + s / 8, bodyY - tabH, x + s / 8 + tabW, bodyY, color);
        g.fill(x + s / 8, bodyY, x + s - s / 8, y + s - s / 6, color);
    }
}