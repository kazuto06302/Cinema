package net.kztmc.mc.cinema.ui.panel.left;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcGroups;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.npc.PlayerHistory;
import net.kztmc.mc.cinema.ui.core.CinemaState;
import net.kztmc.mc.cinema.ui.core.Layout;
import net.kztmc.mc.cinema.ui.core.PanelHost;
import net.kztmc.mc.cinema.ui.core.Tr;
import net.kztmc.mc.cinema.ui.npc.MobCatalog;
import net.kztmc.mc.cinema.ui.npc.MobIcons;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernList;
import net.kztmc.mc.cinema.ui.widget.ModernTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 左パネル: タブ行[NPC一覧 / +モブ追加 / +プレイヤー追加]と、その下のリスト。
 * 画面を開き直しても保持する状態(タブ・検索語・入力中の名前)は static。
 */
public final class LeftPanel {
    public enum Tab { NPCS, MOBS, PLAYERS }

    private static final int MOB_COLS = 3;
    private static final String DEV_WORD = "cinema";   // 検索欄にこれを入力するとdevモードが切り替わる

    private static Tab tab = Tab.NPCS;
    private static String search = "";
    private static String playerInput = "";

    private record Label(String text, int x, int y, int color) {}

    private final List<Label> labels = new ArrayList<>();
    private ModernList<NpcData> npcList;
    private ModernCardGrid<MobCatalog.Entry> mobGrid;
    private ModernCardGrid<String> playerGrid;
    private double npcScroll;
    private double mobScroll;
    private double playerScroll;

    private Layout lay;
    private PanelHost host;

    private boolean probing;
    private NpcData probed;

    private NpcTreeList npcTree;
    private UUID renamingGroup;   // 名前を編集中のグループ
    private UUID revealGroup;     // 作り直したあと、見える位置までスクロールするグループ

    public boolean isMobsTab() {
        return tab == Tab.MOBS;
    }

    /** 作り直す前に、リストのスクロール位置を保存する */
    public void saveScroll() {
        if (npcTree != null) npcScroll = npcTree.getScroll();
        if (mobGrid != null) mobScroll = mobGrid.getScroll();
        if (playerGrid != null) playerScroll = playerGrid.getScroll();
    }

    public void build(Layout lay, PanelHost host) {
        this.lay = lay;
        this.host = host;
        labels.clear();
        npcList = null;
        mobGrid = null;
        playerGrid = null;

        int x = lay.leftX() + lay.pad();
        int w = lay.leftW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);
        int bottom = lay.panelY() + lay.panelH() - lay.pad();

        labels.add(new Label(CinemaState.devMode ? "CINEMA  [DEV]" : "CINEMA", x, y, Theme.ACCENT));
        y += lay.th() + Theme.u(5);

        // --- タブ行: [NPC一覧] [+](+ を押すと、追加のメニューが開く) ---
        int plusW = lay.btnH() + Theme.u(8);
        int listW = w - plusW - lay.gap();
        host.add(new ModernButton(x, y, listW, lay.btnH(), "NPC一覧", ModernButton.Style.GHOST,
                () -> select(Tab.NPCS)).setSelected(tab == Tab.NPCS).setSilent(true));
        int plusX = x + listW + lay.gap();
        int plusY = y + lay.btnH();
        host.add(new ModernButton(plusX, y, plusW, lay.btnH(), "+", ModernButton.Style.GHOST,
                () -> openAddMenu(plusX + plusW - Theme.u(80), plusY)).setSelected(tab != Tab.NPCS).setSilent(true));
        y += lay.btnH() + lay.gap();

        switch (tab) {
            case NPCS -> buildNpcs(x, y, w, bottom);
            case PLAYERS -> buildPlayers(x, y, w, bottom);
            case MOBS -> buildMobs(x, y, w, bottom);
        }
    }

    private void select(Tab t) {
        if (tab != t) UiSounds.tab();
        tab = t;
        host.refresh();
    }

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.leftX(), lay.panelY(), lay.leftW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
    }

    // ---- NPC一覧 ----

    private void buildNpcs(int x, int y, int w, int bottom) {
        npcTree = new NpcTreeList(x, y, w, bottom - y, "NPCはまだいません",
                d -> d.id().equals(NpcManager.selectedId()),
                d -> {   // NPCの選択(トグル)。グループの選択は、NpcManager.select が外す
                    renamingGroup = null;
                    NpcManager.select(d.id().equals(NpcManager.selectedId()) ? null : d.id());
                    host.refresh();
                },
                group -> group.id().equals(NpcGroups.selectedId()),
                group -> {   // グループの選択(トグル)。右パネルが開く
                    renamingGroup = null;
                    NpcGroups.select(group.id().equals(NpcGroups.selectedId()) ? null : group.id());
                    host.refresh();
                },
                group -> {   // ▼ を押したときの、開閉
                    renamingGroup = null;
                    NpcGroups.setOpen(group.id(), !group.isOpen());
                    host.refresh();
                },
                (d, group) -> {   // ドラッグで、グループへ入れる(null なら、外へ出す)
                    renamingGroup = null;
                    NpcGroups.assign(d.id(), group == null ? null : group.id());
                    host.refresh();
                });
        npcTree.setRows(treeRows());
        npcTree.setScroll(npcScroll);
        if (revealGroup != null) {
            npcTree.scrollTo(revealGroup);
            revealGroup = null;
        }
        host.add(npcTree);

        // 名前の編集中: そのグループの行の上に、入力欄を重ねる(入力は、その場で反映する。Enter で閉じる)
        if (renamingGroup != null) {
            NpcGroups.Group group = NpcGroups.find(renamingGroup);
            int[] r = group == null ? null : npcTree.rectOfGroup(group.id());
            if (r == null) {
                renamingGroup = null;   // 行が無い・見えない: 編集を終える
            } else {
                UUID groupId = group.id();
                npcTree.setEditingGroup(groupId);
                int fx = r[0] + Theme.u(8) + Theme.u(3) * 2 + Theme.u(5) - Theme.u(2);   // 矢印のあと
                ModernTextField box = new ModernTextField(fx, r[1], r[0] + r[2] - fx, r[3]);
                box.setMaxLength(32);
                box.setValue(group.name());
                box.setOnChange(s -> NpcGroups.rename(groupId, s));
                box.setOnEnter(s -> {
                    if (s.isBlank()) NpcGroups.rename(groupId, "グループ");
                    renamingGroup = null;
                    host.refresh();
                });
                host.add(box);
                host.focus(box);
            }
        }
    }

    /** 一覧の行: グループ(作った順)と、その中のNPC(開いているときだけ)→ グループに入っていないNPC */
    private static List<NpcTreeList.Row> treeRows() {
        List<NpcData> all = NpcManager.defs();
        List<NpcTreeList.Row> rows = new ArrayList<>();
        for (NpcGroups.Group group : NpcGroups.groups()) {
            List<NpcData> members = all.stream().filter(d -> {
                NpcGroups.Group g = NpcGroups.groupOf(d.id());
                return g != null && g.id().equals(group.id());
            }).toList();
            rows.add(new NpcTreeList.GroupRow(group, members.size()));
            if (group.isOpen()) {
                for (NpcData d : members) rows.add(new NpcTreeList.NpcRow(d, true));
            }
        }
        for (NpcData d : all) {
            if (NpcGroups.groupOf(d.id()) == null) rows.add(new NpcTreeList.NpcRow(d, false));
        }
        return rows;
    }

    // ---- モブ追加 ----

    private void buildMobs(int x, int y, int w, int bottom) {
        // EditBox は標準部品で文字が8px固定のため、検索欄だけ他より少し大きくなる
        ModernTextField box = new ModernTextField(x, y, w, lay.btnH());
        box.setHint("モブを検索...");
        box.setMaxLength(60);
        box.setValue(search);
        host.add(box);
        if (CinemaState.focusSearch) {
            CinemaState.focusSearch = false;
            host.focus(box);
        }
        y += lay.btnH() + lay.gap();

        mobGrid = new ModernCardGrid<>(x, y, w, bottom - y, MOB_COLS, "該当するモブがありません",
                MobCatalog.Entry::name,
                (g, e, ix, iy, size) -> MobIcons.paint(g, host.font(), e.id(), e.name(), ix, iy, size),
                e -> {
                    if (CinemaState.devMode) {
                        // devモード中は、クリックで調整対象を選ぶ(スポーンしない)
                        CinemaState.tuneId = e.id();
                        CinemaState.tuneName = e.name();
                        host.refresh();
                        return;
                    }
                    NpcManager.spawnAtPlayer(NpcData.MOB, e.id(), null);
                    tab = Tab.NPCS;
                    host.refresh();
                });
        mobGrid.setHighlight(e -> CinemaState.devMode && e.id().equals(CinemaState.tuneId));
        mobGrid.setItems(MobCatalog.filter(search, CinemaState.devMode));
        mobGrid.setScroll(mobScroll);
        mobGrid.setLabelScale(0.85f);
        box.setOnChange(s -> {
            if (s.trim().equalsIgnoreCase(DEV_WORD)) {
                toggleDevMode();
                return;
            }
            search = s;
            mobGrid.setItems(MobCatalog.filter(s, CinemaState.devMode));
            mobGrid.setScroll(0);
        });
        host.add(mobGrid);
    }

    /** 検索欄に DEV_WORD が入力されたときの切り替え。入力欄は空に戻す */
    private void toggleDevMode() {
        CinemaState.devMode = !CinemaState.devMode;
        search = "";
        CinemaState.focusSearch = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("[Cinema] devMode " + (CinemaState.devMode ? "ON" : "OFF")));
        }
        host.refresh();
    }

    // ---- プレイヤー追加 ----

    private void buildPlayers(int x, int y, int w, int bottom) {
        int addW = Theme.u(44);
        int fieldW = w - addW - lay.gap();

        ModernTextField box = new ModernTextField(x, y, fieldW, lay.btnH());
        box.setHint("ユーザー名(空欄は既定スキン)");
        box.setMaxLength(16);
        box.setCharFilter(cp -> cp < 128 && (Character.isLetterOrDigit(cp) || cp == '_'));
        box.setValue(playerInput);
        box.setOnChange(s -> playerInput = s);
        box.setOnEnter(this::addPlayer);   // Enterで追加
        host.add(box);
        host.focus(box);

        host.add(new ModernButton(x + fieldW + lay.gap(), y, addW, lay.btnH(), "追加",
                ModernButton.Style.PRIMARY, () -> addPlayer(playerInput)));
        y += lay.btnH() + lay.gap();

        labels.add(new Label("履歴(クリックで追加)", x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(3);

        playerGrid = new ModernCardGrid<String>(x, y, w, bottom - y, MOB_COLS, "履歴はまだありません",
                name -> name,
                (g, name, ix, iy, size) -> MobIcons.paintPlayer(g, host.font(), name, ix, iy, size),
                this::addPlayer);
        playerGrid.setItems(PlayerHistory.names());
        playerGrid.setScroll(playerScroll);
        host.add(playerGrid);
    }

    private void addPlayer(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!name.isEmpty() && !name.matches("[A-Za-z0-9_]{1,16}")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("[Cinema] ユーザー名は半角英数字とアンダースコアで16文字までです"));}
            return;
        }
        NpcManager.spawnAtPlayer(NpcData.PLAYER, "minecraft:player", name.isEmpty() ? null : name);
        if (!name.isEmpty()) PlayerHistory.add(name);
        tab = Tab.NPCS;   // 追加したNPCがすぐ見えるように一覧へ
        host.refresh();
    }

    /**
     * NPCリストの上の右クリック。クリックされた項目を選択して、メニュー(複製・削除)を開く。
     * 項目の特定は、リスト自身のクリックの判定にまかせる(右クリックを、一瞬だけ「クリック」として扱う)。
     * @return リストの上の右クリックなら true(画面は、それ以上処理しない)
     */
    /**
     * NPC一覧の上の右クリック。NPCなら、選択して、メニュー(複製・グループから出す・削除)を開く。
     * グループなら、メニュー(名前を変更・グループを削除)を開く。
     * @return 一覧の上の右クリックなら true(画面は、それ以上処理しない)
     */
    public boolean rightClick(net.minecraft.client.input.MouseButtonEvent event) {
        if (tab != Tab.NPCS || npcTree == null || !npcTree.isMouseOver(event.x(), event.y())) return false;

        NpcTreeList.Row row = npcTree.rowAt(event.y());
        if (row == null) return true;   // 行のない所: 何もしない

        List<ContextMenu.Entry> entries = new ArrayList<>();
        if (row instanceof NpcTreeList.NpcRow nr) {
            UUID id = nr.data().id();
            NpcManager.select(id);
            host.refresh();
            entries.add(new ContextMenu.Entry("複製", () -> {
                NpcManager.duplicate(id);
                host.refresh();
            }, false));
            if (NpcGroups.groupOf(id) != null) {
                entries.add(new ContextMenu.Entry("グループから出す", () -> {
                    NpcGroups.assign(id, null);
                    host.refresh();
                }, false));
            }
            entries.add(new ContextMenu.Entry(Tr.get("cinema.ui.delete"), () -> host.confirmDelete(id), true));
        } else if (row instanceof NpcTreeList.GroupRow gr) {
            UUID groupId = gr.group().id();
            entries.add(new ContextMenu.Entry("名前を変更", () -> {
                renamingGroup = groupId;
                revealGroup = groupId;
                host.refresh();
            }, false));
            entries.add(new ContextMenu.Entry("グループを削除(中のNPCは残す)", () -> {
                NpcGroups.remove(groupId);
                renamingGroup = null;
                host.refresh();
            }, true));
        }
        host.openContextMenu((int) event.x(), (int) event.y(), entries);
        return true;
    }

    /** + のメニュー(モブ・プレイヤー・グループ)。右クリックのメニューと、同じ見た目 */
    private void openAddMenu(int mx, int my) {
        host.openContextMenu(mx, my, List.of(
                new ContextMenu.Entry("モブ", () -> select(Tab.MOBS), false),
                new ContextMenu.Entry("プレイヤー", () -> select(Tab.PLAYERS), false),
                new ContextMenu.Entry("グループ", this::addGroup, false)));
    }

    /** グループを足して、すぐ名前を編集できるようにする */
    private void addGroup() {
        NpcGroups.Group group = NpcGroups.add(NpcGroups.nextDefaultName());
        tab = Tab.NPCS;
        renamingGroup = group.id();
        revealGroup = group.id();
        host.refresh();
    }
}