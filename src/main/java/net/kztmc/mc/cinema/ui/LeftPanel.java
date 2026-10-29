package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.kztmc.mc.cinema.ui.MobCatalog;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.npc.PlayerHistory;
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

    public boolean isMobsTab() {
        return tab == Tab.MOBS;
    }

    /** 作り直す前に、リストのスクロール位置を保存する */
    public void saveScroll() {
        if (npcList != null) npcScroll = npcList.getScroll();
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

        // --- タブ行(角丸なし、すきまなし) ---
        int wNpc = w * 22 / 100;
        int wMob = w * 33 / 100;
        int wAdd = w - wNpc - wMob;
        host.add(new TabButton(x, y, wNpc, lay.btnH(), "NPC一覧", () -> select(Tab.NPCS)).setSelected(tab == Tab.NPCS));
        host.add(new TabButton(x + wNpc, y, wMob, lay.btnH(), "+モブ追加", () -> select(Tab.MOBS)).setSelected(tab == Tab.MOBS));
        host.add(new TabButton(x + wNpc + wMob, y, wAdd, lay.btnH(), "+プレイヤー追加", () -> select(Tab.PLAYERS))
                .setSelected(tab == Tab.PLAYERS));
        y += lay.btnH() + lay.gap();

        switch (tab) {
            case NPCS -> buildNpcs(x, y, w, bottom);
            case PLAYERS -> buildPlayers(x, y, w, bottom);
            case MOBS -> buildMobs(x, y, w, bottom);
        }
    }

    private void select(Tab t) {
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
        npcList = new ModernList<>(x, y, w, bottom - y, "NPCはまだいません",
                d -> NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType()),
                d -> NpcData.PLAYER.equals(d.kind()) ? "Player" : "Mob",
                d -> d.id().equals(NpcManager.selectedId()),
                d -> {
                    NpcManager.select(d.id());
                    host.refresh();
                });
        npcList.setItems(NpcManager.defs());
        npcList.setScroll(npcScroll);
        host.add(npcList);
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
                mc.player.sendOverlayMessage(
                        Component.literal("[Cinema] ユーザー名は半角英数字とアンダースコアで16文字までです"));
            }
            return;
        }
        NpcManager.spawnAtPlayer(NpcData.PLAYER, "minecraft:player", name.isEmpty() ? null : name);
        if (!name.isEmpty()) PlayerHistory.add(name);
        tab = Tab.NPCS;   // 追加したNPCがすぐ見えるように一覧へ
        host.refresh();
    }
}