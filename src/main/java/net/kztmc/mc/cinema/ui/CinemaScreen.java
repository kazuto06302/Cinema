package net.kztmc.mc.cinema.ui;

import java.util.*;
import java.util.function.UnaryOperator;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityOption;
import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.kztmc.mc.cinema.entity.IconStyle;
import net.kztmc.mc.cinema.npc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 撮影用の編集画面(コンパクト版)。
 *   左パネル: タブ行[NPC / モブ追加 / +プレイヤー] と、その下のリスト。
 *             「モブ追加」タブの間は横3カードが入る幅に広がる。
 *   右パネル: 選択中NPCの向き・ポーズ・位置・装備
 *   下パネル: (MobIcons.DEBUG_TUNER が true のときだけ)モブアイコンの大きさ・向きの調整
 * ゲーム画面は暗くしない。上部中央のボタンでUI全体を隠せる。
 * 寸法はすべて Theme.u() 経由で、文字サイズ(Theme.FONT_PX)に連動して縮む。
 */
public class CinemaScreen extends Screen {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SLOT_LABELS = {"メイン", "オフ", "頭", "胴", "脚", "足"};

    private static final int MOB_COLS = 3;

    private enum Tab { NPCS, MOBS, PLAYERS }

    private record Label(String text, int x, int y, int color) {}

    // 画面を開き直しても保持する状態
    private static Tab tab = Tab.NPCS;
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

    /** 診断用: クリックが画面に届いているか、ウィジェットが拾ったかをログに出す */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        Cinema.LOGGER.info("[Cinema] click x={} y={} button={} handled={}",
                event.x(), event.y(), event.button(), handled);
        return handled;
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

        // 寸法は文字サイズに連動
        th = Theme.textH();
        m = Theme.u(6);
        pad = Theme.u(6);
        gap = Theme.u(4);
        panelW = Theme.u(150);
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
        int bottom = panelY + panelH - pad;
        int lh = th + Theme.u(3);   // ラベル1行ぶんの送り

        String name = NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType());
        labels.add(new Label(Theme.fit(this.font, name, w), x, y, Theme.TEXT));
        y += th + Theme.u(5);

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
        y += sliderH + gap;

        // --- ポーズ ---
        labels.add(new Label("ポーズ", x, y, Theme.TEXT_DIM));
        y += lh;
        NpcPose[] poses = NpcPose.values();
        int pgap = Math.max(1, Theme.u(3));
        int pw = (w - pgap * (poses.length - 1)) / poses.length;
        for (int i = 0; i < poses.length; i++) {
            NpcPose p = poses[i];
            addRenderableWidget(new ModernButton(x + i * (pw + pgap), y, pw, btnH, p.label,
                    ModernButton.Style.DEFAULT, () -> {
                edit(id, cur -> cur.withPose(p));
                refresh();
            }).setSelected(d.npcPose() == p));
        }
        y += btnH + gap;

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

        // --- エンティティ固有のオプション(プロフィールが宣言したものだけ) ---
        List<EntityOption> specs = EntityProfiles.of(d.entityType()).options();
        if (!specs.isEmpty()) {
            labels.add(new Label("オプション", x, y, Theme.TEXT_DIM));
            y += lh;
            for (EntityOption spec : specs) {
                addRenderableWidget(new ModernSlider(x, y, w, sliderH, spec.label(), "",
                        spec.min(), spec.max(), d.option(spec.key(), spec.def()),
                        v -> edit(id, cur -> cur.withOption(spec.key(), (int) v))));
                y += sliderH;
            }
            y += gap;
        }

        // --- 位置 ---
        addRenderableWidget(new ModernButton(x, y, w, btnH, "自分の前に移動",
                ModernButton.Style.DEFAULT, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float faceYaw = mc.player.getYRot() + 180f;
            edit(id, cur -> cur.withTransform(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    faceYaw, cur.xRot(), faceYaw, faceYaw));
            refresh();
        }));
        y += btnH + gap;

        // --- 装備 ---
        labels.add(new Label("装備(手持ちを設定)", x, y, Theme.TEXT_DIM));
        y += lh;
        int clearW = btnH;
        int rowGap = Math.max(1, Theme.u(2));
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack cur = d.equipment().get(slot.getName());
            String item = (cur == null || cur.isEmpty()) ? "-" : cur.getHoverName().getString();
            addRenderableWidget(new ModernButton(x, y, w - clearW - rowGap, btnH, SLOT_LABELS[i] + "  " + item,
                    ModernButton.Style.DEFAULT, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                setEquipment(id, slot, mc.player.getMainHandItem().copy());
                refresh();
            }));
            addRenderableWidget(new ModernButton(x + w - clearW, y, clearW, btnH, "×",
                    ModernButton.Style.GHOST, () -> {
                setEquipment(id, slot, ItemStack.EMPTY);
                refresh();
            }));
            y += btnH + rowGap;
        }

        // --- 削除(下端固定) ---
        addRenderableWidget(new ModernButton(x, bottom - btnH, w, btnH, "このNPCを削除",
                ModernButton.Style.DANGER, () -> {
            NpcManager.remove(id);
            refresh();
        }));
    }

    // ================= 編集ヘルパー =================

    private static void edit(UUID id, UnaryOperator<NpcData> fn) {
        NpcManager.get(id).ifPresent(cur -> NpcManager.replace(fn.apply(cur)));
    }

    private static void setEquipment(UUID id, EquipmentSlot slot, ItemStack stack) {
        NpcManager.get(id).ifPresent(cur -> {
            Map<String, ItemStack> map = new HashMap<>(cur.equipment());
            if (stack.isEmpty()) map.remove(slot.getName());
            else map.put(slot.getName(), stack);
            NpcManager.replace(cur.withEquipment(map));
        });
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
    }
}