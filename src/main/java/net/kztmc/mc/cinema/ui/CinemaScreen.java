package net.kztmc.mc.cinema.ui;

import java.util.*;
import java.util.function.UnaryOperator;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.*;
import net.kztmc.mc.cinema.entity.profile.AnimationModeProfile;
import net.kztmc.mc.cinema.npc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 撮影用の編集画面(コンパクト版)。
 *   左パネル: タブ行[NPC / モブ追加 / +プレイヤー] と、その下のリスト。
 *             「モブ追加」タブの間は横3カードが入る幅に広がる。
 *   右パネル: 選択中NPCの編集。名前と削除の下に、タブ[位置・向き / 動き / 装備]。
 *   下パネル: (devモードのときだけ)モブアイコンの大きさ・向きの調整
 * ゲーム画面は暗くしない。上部中央のボタンでUI全体を隠せる。
 * 寸法はすべて Theme.u() 経由で、文字サイズ(Theme.FONT_PX)に連動して縮む。
 */
public class CinemaScreen extends Screen {
    private static final int MOB_COLS = 3;

    private enum Tab { NPCS, MOBS, PLAYERS }

    /** 右パネルのタブ。1つのタブに収まる量にして、パネルの下端からはみ出さないようにする */
    private enum RightTab {
        SPACE("位置・向き"), MOTION("動き"), EQUIP("装備");

        final String label;

        RightTab(String label) {
            this.label = label;
        }
    }

    private record Label(String text, int x, int y, int color) {}

    // 画面を開き直しても保持する状態
    private static Tab tab = Tab.NPCS;
    private static RightTab rightTab = RightTab.SPACE;
    private static String search = "";
    private static boolean collapsed;
    private static boolean openRequested;
    private static String tuneId;     // 調整中のモブ(DEBUG_TUNER用)
    private static String tuneName;

    //player
    private static String playerInput = "";
    private ModernCardGrid<String> playerGrid;
    private double playerScroll;

    //ui
    private final List<Label> labels = new ArrayList<>();
    private ModernList<NpcData> npcList;
    private ModernCardGrid<MobCatalog.Entry> mobGrid;
    private double npcScroll;
    private double mobScroll;
    private boolean pendingRefresh;

    // レイアウト(init時に確定)
    private int m, pad, gap, panelW, leftW, btnH, sliderH, th;
    private int leftX, rightX, panelY, panelH;
    private boolean hasRight;
    private int searchX, searchY, searchW, searchH;
    private boolean hasTuner;
    private int tunerX, tunerY, tunerW, tunerH;

    private static final String DEV_WORD = "cinema";   // 検索欄にこれを入力するとdevモードが切り替わる
    private static boolean devMode;                    // 保存されない(ゲームを閉じると通常に戻る)
    private static boolean focusSearch;                // 切り替え後に検索欄へ入力を戻す
    private ModernDropdown openDropdown;

    // ---- 装備のメッセージ ----
    private String status = "";
    private long statusUntil;
    private int statusY;

    // ---- ドラッグ: 押されているウィジェット、位置欄の現在値 ----
    private Draggable pressed;
    private final double[] pos = new double[3];

    public CinemaScreen() {
        super(Component.literal("Cinema"));
    }

    // ---- 開く処理(チャットが閉じてから開く) ----

    public static void requestOpen() {
        openRequested = true;
    }

    public static void tickOpen(Minecraft mc) {
        if (openRequested && mc.gui.screen() == null && mc.level != null) {
            openRequested = false;
            // 26.2 で Minecraft#setScreen は setScreenAndShow に改名されている
            mc.setScreenAndShow(new CinemaScreen());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** ゲーム画面を暗くしない(撮影構図を見ながら編集するため) */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    // ================= マウス・キー =================

    /** いま、文字を入力している数値欄(なければ null) */
    private ModernNumberField editingField() {
        for (GuiEventListener child : children()) {
            if (child instanceof ModernNumberField field && field.isEditing()) return field;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 入力中の数値欄の外をクリックしたら、確定する
        ModernNumberField editing = editingField();
        if (editing != null && !editing.isMouseOver(event.x(), event.y())) {
            editing.commitEditing();
        }

        // 開いているドロップダウンの選択肢が、他のウィジェットより先にクリックを受ける
        if (openDropdown != null && openDropdown.isOpen()) {
            if (openDropdown.popupClicked(event)) {
                openDropdown = null;
                return true;
            }
            if (!openDropdown.isMouseOver(event.x(), event.y())) {
                openDropdown.close();   // 外をクリックしたら閉じる(ボタン自身のクリックは、ボタンが開閉する)
                openDropdown = null;
            }
        }

        // 通常。ドラッグできるウィジェット(数値欄)を押したら、覚えておく
        boolean handled = super.mouseClicked(event, doubleClick);
        pressed = null;
        if (handled && Theme.isClick(event.button())) {
            getChildAt(event.x(), event.y()).ifPresent(child -> {
                if (child instanceof Draggable draggable) pressed = draggable;
            });
        }
        return handled;
    }

    /** この環境では、ドラッグの標準処理にボタン番号が合わないことがあるので、押したウィジェットへ直接届ける */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed != null) {
            pressed.dragBy(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed != null) {
            Draggable p = pressed;
            pressed = null;
            p.dragEnd(event.x(), event.y());
            return true;
        }
        return super.mouseReleased(event);
    }

    /** 数値を入力している間は、キーを入力欄へ直接渡す(Escで、画面が閉じないように) */
    @Override
    public boolean keyPressed(KeyEvent event) {
        ModernNumberField field = editingField();
        if (field != null) {
            field.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        ModernNumberField field = editingField();
        if (field != null) {
            return field.charTyped(event);
        }
        return super.charTyped(event);
    }

    /** ウィジェットのクリック処理中に作り直すと危ないので、次の描画の頭で実行する */
    private void refresh() {
        pendingRefresh = true;
    }

    /** 検索欄に DEV_WORD が入力されたときの切り替え。入力欄は空に戻す */
    private void toggleDevMode() {
        devMode = !devMode;
        search = "";
        focusSearch = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("[Cinema] devMode " + (devMode ? "ON" : "OFF")));
        }
        refresh();
    }

    @Override
    protected void init() {
        labels.clear();
        npcList = null;
        mobGrid = null;
        hasRight = false;
        hasTuner = false;

        openDropdown = null;
        pressed = null;

        // 寸法は文字サイズに連動
        th = Theme.textH();
        m = Theme.u(6);
        pad = Theme.u(6);
        gap = Theme.u(4);
        panelW = Theme.u(210);
        leftW = Theme.u(210);
        btnH = Math.max(th + 3, Theme.u(14));
        sliderH = th + Theme.u(11);

        panelY = m;
        panelH = this.height - m * 2;
        leftX = m;
        rightX = this.width - panelW - m;

        int pillW = Theme.u(56);
        int pillX = (this.width - (pillW * 2 + gap)) / 2;
        addRenderableWidget(new ModernButton(pillX, m, pillW, btnH,
                collapsed ? "UI表示" : "UI非表示", ModernButton.Style.DEFAULT, () -> {
            collapsed = !collapsed;
            refresh();
        }));
        if (collapsed) return;

        buildLeft();
        NpcManager.selectedData().ifPresent(d -> {
            hasRight = true;
            buildRight(d);
        });
        if (devMode && tab == Tab.MOBS) buildTuner();
    }

    // ================= 左パネル =================

    private void buildLeft() {
        int x = leftX + pad;
        int w = leftW - pad * 2;
        int y = panelY + Theme.u(8);
        int bottom = panelY + panelH - pad;

        labels.add(new Label(devMode ? "CINEMA  [DEV]" : "CINEMA", x, y, Theme.ACCENT));
        y += th + Theme.u(5);

        // --- タブ行: [NPC] [モブ追加] [+ プレイヤー] ---
        int usable = w - gap * 2;
        int wNpc = usable * 22 / 100;
        int wMob = usable * 33 / 100;
        int wAdd = usable - wNpc - wMob;

        addRenderableWidget(new ModernButton(x, y, wNpc, btnH, "NPC一覧",
                ModernButton.Style.GHOST, () -> {
            tab = Tab.NPCS;
            refresh();
        }).setSelected(tab == Tab.NPCS));
        addRenderableWidget(new ModernButton(x + wNpc + gap, y, wMob, btnH, "+モブ追加",
                ModernButton.Style.GHOST, () -> {
            tab = Tab.MOBS;
            refresh();
        }).setSelected(tab == Tab.MOBS));
        addRenderableWidget(new ModernButton(x + wNpc + wMob + gap * 2, y, wAdd, btnH, "+プレイヤー追加",
                ModernButton.Style.GHOST, () -> {
            tab = Tab.PLAYERS;
            refresh();
        }).setSelected(tab == Tab.PLAYERS));
        y += btnH + gap;

        if (tab == Tab.NPCS) {
            npcList = new ModernList<>(x, y, w, bottom - y, "NPCはまだいません",
                    d -> NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType()),
                    d -> NpcData.PLAYER.equals(d.kind()) ? "Player" : "Mob",
                    d -> d.id().equals(NpcManager.selectedId()),
                    d -> {
                        NpcManager.select(d.id());
                        refresh();
                    });
            npcList.setItems(NpcManager.defs());
            npcList.setScroll(npcScroll);
            addRenderableWidget(npcList);
        } else if (tab == Tab.PLAYERS) {
            buildPlayerTab(x, y, w, bottom);
        } else {
            // EditBox は標準部品で文字が8px固定のため、検索欄だけ他より少し大きくなる
            searchX = x;
            searchY = y;
            searchW = w;
            searchH = btnH;
            ModernTextField box = new ModernTextField(x, y, w, searchH);
            box.setHint("モブを検索...");
            box.setMaxLength(60);
            box.setValue(search);
            addRenderableWidget(box);
            if (focusSearch) {
                focusSearch = false;
                setInitialFocus(box);
            }
            y += searchH + gap;

            mobGrid = new ModernCardGrid<>(x, y, w, bottom - y, MOB_COLS, "該当するモブがありません",
                    MobCatalog.Entry::name,
                    (g, e, ix, iy, size) -> MobIcons.paint(g, this.font, e.id(), e.name(), ix, iy, size),
                    e -> {
                        if (devMode) {
                            // devモード中は、クリックで調整対象を選ぶ(スポーンしない)
                            tuneId = e.id();
                            tuneName = e.name();
                            refresh();
                            return;
                        }
                        NpcManager.spawnAtPlayer(NpcData.MOB, e.id(), null);
                        tab = Tab.NPCS;
                        refresh();
                    });
            mobGrid.setHighlight(e -> devMode && e.id().equals(tuneId));
            mobGrid.setItems(MobCatalog.filter(search, devMode));
            mobGrid.setScroll(mobScroll);
            mobGrid.setLabelScale(0.85f);
            box.setOnChange(s -> {
                if (s.trim().equalsIgnoreCase(DEV_WORD)) {
                    toggleDevMode();
                    return;
                }
                search = s;
                mobGrid.setItems(MobCatalog.filter(s, devMode));
                mobGrid.setScroll(0);
            });
            addRenderableWidget(mobGrid);
        }
    }

    private void buildPlayerTab(int x, int y, int w, int bottom) {
        int addW = Theme.u(44);
        int fieldW = w - addW - gap;

        ModernTextField box = new ModernTextField(x, y, fieldW, btnH);
        box.setHint("ユーザー名(空欄は既定スキン)");
        box.setMaxLength(16);
        box.setCharFilter(cp -> cp < 128 && (Character.isLetterOrDigit(cp) || cp == '_'));
        box.setValue(playerInput);
        box.setOnChange(s -> playerInput = s);
        box.setOnEnter(this::addPlayer);   // Enterで追加
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(new ModernButton(x + fieldW + gap, y, addW, btnH, "追加",
                ModernButton.Style.PRIMARY, () -> addPlayer(playerInput)));
        y += btnH + gap;

        labels.add(new Label("履歴(クリックで追加)", x, y, Theme.TEXT_DIM));
        y += th + Theme.u(3);

        playerGrid = new ModernCardGrid<String>(x, y, w, bottom - y, MOB_COLS, "履歴はまだありません",
                name -> name,
                (g, name, ix, iy, size) -> MobIcons.paintPlayer(g, this.font, name, ix, iy, size),
                this::addPlayer);
        playerGrid.setItems(PlayerHistory.names());
        playerGrid.setScroll(playerScroll);
        addRenderableWidget(playerGrid);
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
        refresh();
    }

    // ================= 下パネル(アイコン調整) =================

    private void buildTuner() {
        hasTuner = true;
        tunerW = Theme.u(170);
        int titleH = th + Theme.u(5);
        tunerH = pad * 2 + titleH + sliderH * 6 + gap + btnH;
        tunerX = (this.width - tunerW) / 2;
        tunerY = this.height - m - tunerH;

        int x = tunerX + pad;
        int w = tunerW - pad * 2;
        int y = tunerY + pad;

        String title = "アイコン調整" + (tuneId == null ? "" : ": " + tuneName);
        labels.add(new Label(Theme.fit(this.font, title, w), x, y, Theme.ACCENT));
        y += titleH;

        // 全モブ共通の大きさ
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "全体の大きさ", "%", 30, 120,
                Math.round(MobIcons.getGlobalFit() * 100f),
                v -> MobIcons.setGlobalFit((float) v / 100f)));
        y += sliderH;

        if (tuneId == null) {
            labels.add(new Label("カードをクリックして選択", x, y + Theme.u(3), Theme.TEXT_DIM));
            return;
        }

        String id = tuneId;
        IconStyle s = MobIcons.styleOf(id);

        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "このモブの大きさ", "%", 30, 150,
                Math.round(s.fit() * 100f),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withFit((float) v / 100f))));
        y += sliderH;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "横回転", "°", -180, 180, s.yaw(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withYaw((float) v))));
        y += sliderH;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "縦回転", "°", -90, 90, s.pitch(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withPitch((float) v))));
        y += sliderH;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "傾き(Z回転)", "°", -180, 180, s.roll(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withRoll((float) v))));
        y += sliderH;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "高さ", "%", -100, 100, s.height(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withHeight((float) v))));
        y += sliderH + gap;

        int half = (w - gap) / 2;
        addRenderableWidget(new ModernButton(x, y, half, btnH, "行をコピー",
                ModernButton.Style.PRIMARY, () -> {
            String line = MobIcons.styleLine(id);
            Minecraft.getInstance().keyboardHandler.setClipboard(line);
            Cinema.LOGGER.info("[Cinema] {}   (FIT={})", line, MobIcons.getGlobalFit());
        }));
        addRenderableWidget(new ModernButton(x + half + gap, y, half, btnH, "リセット",
                ModernButton.Style.DEFAULT, () -> {
            MobIcons.clearDebugStyle(id);
            refresh();
        }));
    }

    // ================= 右パネル =================

    private void buildRight(NpcData d) {
        UUID id = d.id();
        int x = rightX + pad;
        int w = panelW - pad * 2;
        int y = panelY + Theme.u(8);

        // --- 最上段: 名前(左)と、削除(右) ---
        String name = NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType());
        int delW = Theme.u(44);
        labels.add(new Label(Theme.fit(this.font, name, w - delW - gap), x, y + (btnH - th) / 2, Theme.TEXT));
        addRenderableWidget(new ModernButton(x + w - delW, y, delW, btnH, "削除",
                ModernButton.Style.DANGER, () -> {
            NpcManager.remove(id);
            refresh();
        }));
        y += btnH + Theme.u(5);

        // --- タブ行: [位置・向き] [動き] [装備] ---
        RightTab[] tabs = RightTab.values();
        int tgap = Math.max(1, Theme.u(3));
        int tw = (w - tgap * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            RightTab t = tabs[i];
            addRenderableWidget(new ModernButton(x + i * (tw + tgap), y, tw, btnH, t.label,
                    ModernButton.Style.GHOST, () -> {
                rightTab = t;
                refresh();
            }).setSelected(rightTab == t));
        }
        y += btnH + gap;

        switch (rightTab) {
            case SPACE -> buildSpace(d, x, y, w);
            case MOTION -> buildMotion(d, x, y, w);
            case EQUIP -> buildEquip(d, x, y, w);
        }
    }

    // ---- タブ「位置・向き」 ----

    private void buildSpace(NpcData d, int x, int y, int w) {
        UUID id = d.id();
        int lh = th + Theme.u(3);   // ラベル1行ぶんの送り

        // --- 位置(Position): X/Y/Z。入力・左右ドラッグ・マウスホイールで動かせる(Shiftで×10、Ctrlで×100)---
        labels.add(new Label("位置", x, y, Theme.TEXT_DIM));
        y += lh;
        pos[0] = d.x();
        pos[1] = d.y();
        pos[2] = d.z();
        String[] axisNames = {"X", "Y", "Z"};
        int[] axisColors = {0xFFE5534B, 0xFF57AB5A, 0xFF539BF5};
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            addRenderableWidget(new ModernNumberField(x, y, w, sliderH, axisNames[i], axisColors[i], pos[i], v -> {
                pos[axis] = v;
                edit(id, cur -> cur.withTransform(pos[0], pos[1], pos[2],
                        cur.yRot(), cur.xRot(), cur.headYRot(), cur.bodyYRot()));
            }));
            y += sliderH;
        }
        addRenderableWidget(new ModernButton(x, y, w, btnH, "自分の前に移動",
                ModernButton.Style.DEFAULT, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float faceYaw = mc.player.getYRot() + 180f;
            edit(id, cur -> cur.withTransform(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    faceYaw, cur.xRot(), faceYaw, faceYaw));
            refresh();   // 位置欄の表示も、新しい値にする
        }));
        y += btnH + gap;

        // --- 向き ---
        labels.add(new Label("向き", x, y, Theme.TEXT_DIM));
        y += lh;

        float body = Mth.wrapDegrees(d.bodyYRot());
        float headOffset = Mth.clamp(Mth.wrapDegrees(d.headYRot() - d.bodyYRot()), -75f, 75f);

        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "体の向き", "°", -180, 180, body, v ->
                edit(id, cur -> {
                    float bo = (float) v;
                    float off = Mth.clamp(Mth.wrapDegrees(cur.headYRot() - cur.bodyYRot()), -75f, 75f);
                    float head = bo + off;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, bo);
                })));
        y += sliderH;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "頭の左右", "°", -75, 75, headOffset, v ->
                edit(id, cur -> {
                    float head = cur.bodyYRot() + (float) v;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, cur.bodyYRot());
                })));
        y += sliderH;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "頭の上下", "°", -90, 90, d.xRot(), v ->
                edit(id, cur -> cur.withTransform(cur.x(), cur.y(), cur.z(), cur.yRot(), (float) v,
                        cur.headYRot(), cur.bodyYRot()))));
    }

    // ---- タブ「動き」 ----

    private void buildMotion(NpcData d, int x, int y, int w) {
        UUID id = d.id();
        int lh = th + Theme.u(3);

        // --- ポーズ(姿勢)。モブごとに、意味のあるものだけを出す(選べるものがなければ、欄ごと出さない) ---
        int pgap = Math.max(1, Theme.u(3));
        List<NpcPose> poses = NpcData.PLAYER.equals(d.kind())
                ? NpcPose.PLAYER : EntityProfiles.of(d.entityType()).poses();
        if (poses.size() > 1) {
            labels.add(new Label("ポーズ", x, y, Theme.TEXT_DIM));
            y += lh;
            int pw = (w - pgap * (poses.size() - 1)) / poses.size();
            for (int i = 0; i < poses.size(); i++) {
                NpcPose p = poses.get(i);
                addRenderableWidget(new ModernButton(x + i * (pw + pgap), y, pw, btnH, p.label,
                        ModernButton.Style.DEFAULT, () -> {
                    edit(id, cur -> cur.withPose(p));
                    refresh();
                }).setSelected(d.npcPose() == p));
            }
            y += btnH + gap;
        }

        // --- モデル(腕の太さ): プレイヤーのみ ---
        if (NpcData.PLAYER.equals(d.kind())) {
            labels.add(new Label("モデル(腕の太さ)", x, y, Theme.TEXT_DIM));
            y += lh;
            String[] modelLabels = {"自動", "ノーマル", "スリム"};
            String[] modelValues = {null, "wide", "slim"};
            int mw = (w - pgap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                String value = modelValues[i];
                addRenderableWidget(new ModernButton(x + i * (mw + pgap), y, mw, btnH, modelLabels[i],
                        ModernButton.Style.DEFAULT, () -> {
                    edit(id, cur -> cur.withModel(Optional.ofNullable(value)));
                    refresh();
                }).setSelected(java.util.Objects.equals(d.model().orElse(null), value)));
            }
            y += btnH + gap;
        }

        // --- アニメーション(NPCは常に停止。時刻をtickで指定する) ---
        labels.add(new Label("アニメーション", x, y, Theme.TEXT_DIM));
        y += lh;
        addRenderableWidget(new ModernSlider(x, y, w, sliderH, "時間", "tick", 0,
                EntityTweaks.animTickMax(d.entityType()), 0.1, d.animTickOrDefault(),
                v -> edit(id, cur -> cur.withAnimTick((float) v))));
        y += sliderH + gap;

        // --- 動き(歩行・腕)。アニメーションの時間とは独立。将来のPose(部位ごとの調整)は、この上に重ねる ---
        Entity live = NpcManager.entityOf(id);
        boolean living = live == null || live instanceof LivingEntity;   // エンドクリスタルやボートは、生き物ではない
        boolean isPlayer = NpcData.PLAYER.equals(d.kind());
        boolean showWalk = living && (isPlayer || EntityProfiles.of(d.entityType()).walks());
        boolean showArms = living && (isPlayer || EntityProfiles.of(d.entityType()).isHumanoid());

        if (showWalk || showArms) {
            labels.add(new Label("動き", x, y, Theme.TEXT_DIM));
            y += lh;
        }
        if (showWalk) {
            addRenderableWidget(new ModernSlider(x, y, w, sliderH, "歩行", "%", -100, 100,
                    d.option(EntityMotion.WALK, 0),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.WALK, (int) v))));
            y += sliderH;
        }
        if (showArms) {
            int last = EntityMotion.ARM_NAMES.length - 1;
            ModernSlider arms = new ModernSlider(x, y, w, sliderH, "腕", "", 0, last,
                    d.option(EntityMotion.ARMS, 0),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.ARMS, (int) v)));
            arms.setValueFormatter(v -> EntityMotion.ARM_NAMES[Math.max(0, Math.min(last, (int) Math.round(v)))]);
            addRenderableWidget(arms);
            y += sliderH;
        }

        if (devMode && showWalk) {
            EntityProfile walker = EntityProfiles.of(d.entityType());
            if (walker.walkCyclePosition() > 0f) {
                addRenderableWidget(new ModernSlider(x, y, w, sliderH, "[DEV] 歩行周期", "秒", 0.2, 6.0, 0.05,
                        walker.walkCycleSeconds(), v -> {
                    walker.setWalkCycleSeconds((float) v);
                    edit(id, cur -> cur);   // 歩行を、新しい周期で反映し直す
                }));
                y += sliderH;
                addRenderableWidget(new ModernSlider(x, y, w, sliderH, "[DEV] 歩行の強さ", "", 0.05, 1.0, 0.05,
                        walker.walkKeyframeIntensity(), v -> {
                    walker.setWalkKeyframeIntensity((float) v);
                    edit(id, cur -> cur);
                }));
                y += sliderH;
                addRenderableWidget(new ModernButton(x, y, w, btnH, "周期をコピー", ModernButton.Style.DEFAULT, () -> {
                    String line = String.format(java.util.Locale.ROOT, "walkKeyframe(%.2ff, %.1ff, %.2ff);",
                            walker.walkCycleSeconds(), walker.walkMaxAnimationSpeed(), walker.walkKeyframeIntensity());
                    Minecraft.getInstance().keyboardHandler.setClipboard(line);
                    Cinema.LOGGER.info("[Cinema] {} -> {}", d.entityType(), line);
                }));
                y += btnH + gap;
            }
        }

        if (showWalk || showArms) {
            y += gap;
        }

        // --- エンティティ固有のオプション(プロフィールが宣言したものだけ) ---
        List<EntityOption> specs = EntityProfiles.of(d.entityType()).options();
        if (!specs.isEmpty()) {
            for (EntityOption spec : specs) {
                if (spec.kind() == EntityOption.Kind.CHECKBOX) {
                    addRenderableWidget(new ModernCheckbox(x, y, w, sliderH, spec.label(),
                            d.option(spec.key(), spec.def()) == 1,
                            on -> edit(id, cur -> cur.withOption(spec.key(), on ? 1 : 0))));
                    y += sliderH;
                    continue;
                }
                if (spec.kind() == EntityOption.Kind.DROPDOWN) {
                    addRenderableWidget(new ModernDropdown(x, y, w, sliderH, spec.label(), spec.names(),
                            d.option(spec.key(), spec.def()),
                            i -> edit(id, cur -> cur.withOption(spec.key(), i)),
                            this::onDropdownOpen));
                    y += sliderH;
                    continue;
                }

                ModernSlider slider = new ModernSlider(x, y, w, sliderH, spec.label(), "",
                        spec.min(), spec.max(), d.option(spec.key(), spec.def()),
                        v -> edit(id, cur -> cur.withOption(spec.key(), (int) v)));
                if (!spec.names().isEmpty()) {
                    slider.setValueFormatter(v -> {
                        int i = Math.max(0, Math.min(spec.names().size() - 1, (int) Math.round(v)));
                        return spec.names().get(i);
                    });
                }
                addRenderableWidget(slider);
                y += sliderH;
            }
            y += gap;
        }

        // --- [DEV] 動作の長さの調整(AnimationState を使うモブ)---
        if (devMode && EntityProfiles.of(d.entityType()) instanceof AnimationModeProfile moded) {
            int modeIdx = d.option(moded.optionKey(), 0);
            if (moded.hasField(modeIdx)) {
                addRenderableWidget(new ModernSlider(x, y, w, sliderH, "[DEV] 動作の長さ", "秒", 0.0, 12.0, 0.05,
                        moded.secondsOf(modeIdx), v -> {
                    moded.setSeconds(modeIdx, (float) v);
                    edit(id, cur -> cur);   // 新しい長さで反映し直す
                }));
                y += sliderH;
                addRenderableWidget(new ModernButton(x, y, w, btnH, "長さをコピー", ModernButton.Style.DEFAULT, () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(moded.secondsLine());
                    Cinema.LOGGER.info("[Cinema] {} seconds -> {}", d.entityType(), moded.secondsLine());
                }));
            }
        }
    }

    // ---- タブ「装備」 ----

    private void buildEquip(NpcData d, int x, int y, int w) {
        UUID id = d.id();
        int lh = th + Theme.u(3);

        // --- 装備(アイコン)。上段: インベントリ / メイン / オフ / 全削除、下段: 頭 / 胸 / 脚 / 足 ---
        // スロットは、クリックで外す。右クリックで装飾(エンチャント光沢・トリム)。入れるのは、インベントリの画面から
        labels.add(new Label("装備(クリックで外す・右クリックで装飾)", x, y, Theme.TEXT_DIM));
        y += lh;

        boolean animalGear = EntityProfiles.of(d.entityType()).hasAnimalGear();
        int cgap = Math.max(1, Theme.u(3));
        int cw = (w - cgap * 3) / 4;
        int ch = Math.max(Theme.u(26), 26);

        addRenderableWidget(new ItemButton(x, y, cw, ch, ItemStack.EMPTY, new ItemStack(Items.CHEST), b ->
                openInventory()).tip("インベントリを開く(アイテムを持ってきて、装備させる)"));
        addSlotButton(d, EquipmentSlot.MAINHAND, x + (cw + cgap), y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.OFFHAND, x + (cw + cgap) * 2, y, cw, ch, animalGear);
        addRenderableWidget(new ItemButton(x + (cw + cgap) * 3, y, cw, ch, ItemStack.EMPTY,
                new ItemStack(Items.BARRIER), b -> {
            for (EquipmentSlot s : EquipmentEdit.SLOTS) EquipmentEdit.set(id, s, ItemStack.EMPTY);
            refresh();
        }).tip("装備をすべて外す"));
        y += ch + cgap;

        addSlotButton(d, EquipmentSlot.HEAD, x, y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.CHEST, x + (cw + cgap), y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.LEGS, x + (cw + cgap) * 2, y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.FEET, x + (cw + cgap) * 3, y, cw, ch, animalGear);
        y += ch + gap;

        // --- 手の動作(使えるアイテムを持っているときだけ)---
        ItemStack mainItem = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);
        ItemStack offItem = EquipmentEdit.stackOf(d, EquipmentSlot.OFFHAND);
        boolean anyMotion = EntityMotion.isSpear(mainItem) || hasUseMotion(mainItem) || hasUseMotion(offItem)
                || mainItem.is(Items.CROSSBOW) || offItem.is(Items.CROSSBOW);
        if (anyMotion) {
            labels.add(new Label("手の動作", x, y, Theme.TEXT_DIM));
            y += lh;
        }

        if (EntityMotion.isSpear(mainItem)) {
            // 槍: 突き(ジャブ)と突進(チャージ)は、別の動作。どちらの進み具合も、同じスライダーで決める
            addRenderableWidget(new ModernDropdown(x, y, w, sliderH, "槍の攻撃", List.of(EntityMotion.SPEAR_NAMES),
                    d.option(EntityMotion.SPEAR, 0),
                    i -> edit(id, cur -> cur.withOption(EntityMotion.SPEAR, i)),
                    this::onDropdownOpen));
            y += sliderH;
            addRenderableWidget(new ModernSlider(x, y, w, sliderH, "槍の進み具合", "%", 0, 100,
                    d.option(EntityMotion.SPEAR_AMOUNT, 50),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.SPEAR_AMOUNT, (int) v))));
            y += sliderH;
        } else if (hasUseMotion(mainItem)) {
            addRenderableWidget(new ModernSlider(x, y, w, sliderH, "メイン 使用モーション", "%", 0, 100,
                    d.option(EntityMotion.USE_MAIN, EntityMotion.defaultUse(mainItem)),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.USE_MAIN, (int) v))));
            y += sliderH;
        }
        if (hasUseMotion(offItem)) {
            addRenderableWidget(new ModernSlider(x, y, w, sliderH, "オフ 使用モーション", "%", 0, 100,
                    d.option(EntityMotion.USE_OFF, EntityMotion.defaultUse(offItem)),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.USE_OFF, (int) v))));
            y += sliderH;
        }
        if (mainItem.is(Items.CROSSBOW) || offItem.is(Items.CROSSBOW)) {
            addRenderableWidget(new ModernCheckbox(x, y, w, sliderH, "装填済み(矢をつがえて構える)",
                    d.option(EntityMotion.LOADED, 0) == 1,
                    on -> edit(id, cur -> cur.withOption(EntityMotion.LOADED, on ? 1 : 0))));
            y += sliderH;
        }
        statusY = y + Theme.u(2);   // メッセージの行
    }

    // ================= 装備 =================

    /** 使用モーション(弓・クロスボウ・トライデント・盾・食べ物など)があるアイテムか */
    private static boolean hasUseMotion(ItemStack stack) {
        return !stack.isEmpty() && !"NONE".equals(stack.getUseAnimation().name());
    }

    /** 標準のインベントリを開く。クリエイティブなら、タブつきのクリエイティブ画面 */
    private static void openInventory() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (player.getAbilities().instabuild) {
            mc.setScreenAndShow(new CinemaCreativeScreen(player, player.connection.enabledFeatures(),
                    mc.options.operatorItemsTab().get()));
        } else {
            mc.setScreenAndShow(new CinemaInventoryScreen(player));
        }
    }

    private void addSlotButton(NpcData d, EquipmentSlot slot, int bx, int by, int bw, int bh, boolean animalGear) {
        UUID id = d.id();
        ItemStack stack = EquipmentEdit.stackOf(d, slot);
        String label = EquipmentEdit.label(slot, animalGear);
        ItemButton button = new ItemButton(bx, by, bw, bh, stack, EquipmentEdit.ghostOf(slot), b -> {
            if (stack.isEmpty()) {
                say(label + "は空です(インベントリを開いて、アイテムを入れられます)");
                return;
            }
            if (b == 0) {   // 左クリック: 外す
                EquipmentEdit.set(id, slot, ItemStack.EMPTY);
                refresh();
            } else {        // 右クリック: 装飾
                Minecraft.getInstance().setScreenAndShow(new EquipmentMenuScreen(this, label,
                        () -> NpcManager.get(id).map(cur -> EquipmentEdit.stackOf(cur, slot)).orElse(ItemStack.EMPTY),
                        s -> EquipmentEdit.set(id, slot, s)));
            }
        });
        button.tip(stack.isEmpty() ? label : label + ": " + stack.getHoverName().getString());
        addRenderableWidget(button);
    }

    private void say(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    // ================= 編集ヘルパー =================

    private static void edit(UUID id, UnaryOperator<NpcData> fn) {
        NpcManager.get(id).ifPresent(cur -> NpcManager.replace(fn.apply(cur)));
    }

    // ================= 描画 =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (pendingRefresh) {
            pendingRefresh = false;
            if (npcList != null) npcScroll = npcList.getScroll();
            if (mobGrid != null) mobScroll = mobGrid.getScroll();
            if (playerGrid != null) playerScroll = playerGrid.getScroll();
            rebuildWidgets();
        }

        if (!collapsed) {
            Theme.panel(g, leftX, panelY, leftW, panelH);
            if (hasRight) Theme.panel(g, rightX, panelY, panelW, panelH);
            if (hasTuner) Theme.panel(g, tunerX, tunerY, tunerW, tunerH);
            for (Label l : labels) {
                Theme.text(g, this.font, l.text(), l.x(), l.y(), l.color());
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);

        if (openDropdown != null && openDropdown.isOpen()) {
            openDropdown.extractPopup(g, mouseX, mouseY);
        }

        // 装備: 操作できなかった理由
        if (hasRight && !collapsed && rightTab == RightTab.EQUIP && System.currentTimeMillis() < statusUntil) {
            Theme.text(g, this.font, Theme.fit(this.font, status, panelW - pad * 2),
                    rightX + pad, statusY, 0xFFE5A04B);
        }
    }

    private void onDropdownOpen(ModernDropdown dropdown) {
        if (openDropdown != null && openDropdown != dropdown) openDropdown.close();
        openDropdown = dropdown;
    }
}