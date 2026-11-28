package net.kztmc.mc.cinema.ui.panel.right;

import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.*;
import net.kztmc.mc.cinema.npc.*;
import net.kztmc.mc.cinema.ui.core.*;
import net.kztmc.mc.cinema.ui.panel.PartSelection;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.panel.inventory.CinemaCreativeScreen;
import net.kztmc.mc.cinema.ui.panel.left.NpcFaces;
import net.kztmc.mc.cinema.ui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 右パネル: 選択中のNPCの編集
//   最上段: 名前と削除
//   タブ  : [基本(2) | 調整(1)]
//   基本  : 位置・サイズ・向き・状態・姿勢・アニメーション・動き・固有オプション・装備・手の動作
//   調整  : パーツごとのポーズ(準備中)
// 基本の中身は長いので、ホイールと右端のスクロールバーで、上下にスクロールできる
// 各セクションは、見出しをクリックで、畳める
public final class RightPanel implements ScrollPanel{
    private enum RightTab {
        MAIN("cinema.ui.right.basic"), POSE("cinema.ui.right.parts");
        final String label;
        RightTab(String label) {
            this.label = label;
        }
    }

    // ラベル。heading が true なら、見出し(右に線を引く)
    private record Label(String text, int x, int y, int color, boolean heading) {
        Label(String text, int x, int y, int color) {
            this(text, x, y, color, false);
        }
    }

    // スクロールする部品。hidden は、畳んだセクションの中身
    private static final class Item {
        final AbstractWidget widget;
        final int baseY;
        boolean hidden;

        Item(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }

    // 畳んだセクション(画面を開き直しても保持する)
    private static final Set<String> collapsed = new HashSet<>();

    private static RightTab tab = RightTab.MAIN;
    private static int scroll;   // 作り直しても保持する

    private final List<Label> fixedLabels = new ArrayList<>();    // スクロールしない(名前)
    private final List<Label> scrollLabels = new ArrayList<>();   // スクロールする
    private final List<Item> items = new ArrayList<>();           // スクロールするウィジェット
    private final List<int[]> sectionSpans = new ArrayList<>();   // 各セクションの上と下(スクロール前の座標)

    private Layout lay;
    private PanelHost host;
    private UUID id;
    private int x, w, contentTop, contentBottom, maxScroll;
    private int baseX, baseW;          // 見出しとカードの位置(インデントなし)
    private String currentKey = "";    // 組み立て中のセクション
    private boolean headerMade;        // そのセクションの、最初の見出しを作ったか
    private final double[] pos = new double[3];
    private int backX, backY, backW, backH;   // 戻るボタンの位置(後ろに、小さなパネルを敷く)

    private String status = "";
    private long statusUntil;
    private int statusY;

    private boolean dragScrolling;
    private long wheelLockUntil;

    private int iconX, iconY, iconSize;

    private UUID partsNpc;                // 編集中のパーツが、どの NPC のものか(NPC が変わったら、一覧に戻す)
    private int editorTop;                // パーツのタブの、下半分(値の設定)の上端
    private int panelBottom;              // 右パネルの中身の下端
    private boolean fixedMode;            // true の間は、固定の欄(スクロールしない)に追加する

    // ================= 組み立て =================

    public void build(Layout lay, PanelHost host, NpcData d) {
        this.lay = lay;
        this.host = host;
        this.id = d.id();
        fixedLabels.clear();
        scrollLabels.clear();
        sectionSpans.clear();
        items.clear();
        if (!d.id().equals(partsNpc)) {
            partsNpc = d.id();
            PartSelection.select(null);
        }
        PartGlow.setActive(tab == RightTab.POSE);

        x = lay.rightX() + lay.pad();
        w = lay.panelW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);

        // 最上段: 顔の画像と名前(左)、削除(右)。名前の上にカーソルを置くと、IDを出す
        String name = NpcNames.of(d);
        int delW = Theme.u(44);
        iconSize = lay.btnH();
        iconX = x;
        iconY = y;
        int nameX = x + iconSize + lay.gap();
        String shownName = Theme.fit(host.font(), name, w - delW - iconSize - lay.gap() * 2);
        fixedLabels.add(new Label(shownName, nameX, y + (lay.btnH() - lay.th()) / 2, Theme.TEXT));
        String idText = NpcData.PLAYER.equals(d.kind()) ? d.entityType() + " (" + d.skin().orElse("") + ")" : d.entityType();
        host.add(new NameTip(nameX, y, Math.max(1, Theme.textWidth(host.font(), shownName)), lay.btnH(), idText));
        UUID npcId = id;

        // 削除
        host.add(new ModernButton(x + w - delW, y, delW, lay.btnH(), Tr.get("cinema.ui.delete"), ModernButton.Style.DANGER, () -> host.confirmDelete(npcId)));

        // ← (戻る): 右パネルの左の外に、独立したボタンとして出す(選択を外して、ワールド設定へ戻る)
        backW = Theme.u(26);
        backH = lay.btnH() + Theme.u(4);
        backX = lay.rightX() - backW - lay.gap();
        backY = lay.panelY();
        host.add(new ModernButton(backX, backY, backW, backH, "←", ModernButton.Style.DEFAULT, () -> {
            NpcManager.select(null);
            host.refresh();
        }));

        y += lay.btnH() + Theme.u(5);

        // タブ行(2:1、角丸なし)
        int mainW = w * 2 / 3;
        host.add(new TabButton(x, y, mainW, lay.btnH(), Tr.get(RightTab.MAIN.label), () -> selectTab(RightTab.MAIN)).setSelected(tab == RightTab.MAIN));
        host.add(new TabButton(x + mainW, y, w - mainW, lay.btnH(), Tr.get(RightTab.POSE.label), () -> selectTab(RightTab.POSE)).setSelected(tab == RightTab.POSE));
        y += lay.btnH() + lay.gap();

        contentTop = y;
        contentBottom = lay.panelY() + lay.panelH() - lay.pad();

        // スクロールバーの分だけ、中身の幅を狭くする
        int barW = Theme.u(5);
        int fullW = w;
        w = fullW - barW - Theme.u(2);
        baseX = x;
        baseW = w;

        int contentStart = y + Theme.u(4);   // 内容の始まりを、少し下げる
        int end = tab == RightTab.MAIN ? buildMain(d, contentStart) : buildParts(d, contentStart);

        maxScroll = Math.max(0, end - contentBottom);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        host.add(new ScrollBar(x + fullW - barW, contentTop, barW, contentBottom - contentTop));
        w = fullW;
        applyScroll();
    }

    private void selectTab(RightTab t) {
        if (tab != t) UiSounds.tab();
        if (t == RightTab.POSE && tab != RightTab.POSE) {
            // 左は、パーツ一覧にする(いま開いているパネルは、閉じる)。戻るときのために、覚えておく
            CinemaState.mainWasOpen = CinemaState.leftView == CinemaState.LeftView.MAIN;
            CinemaState.leftRequest = CinemaState.LeftView.PARTS;
        } else if (t == RightTab.MAIN && CinemaState.leftView == CinemaState.LeftView.PARTS) {
            // パーツ一覧を閉じて、開いていたパネルを元に戻す
            CinemaState.leftRequest = CinemaState.mainWasOpen
                    ? CinemaState.LeftView.MAIN : CinemaState.LeftView.NONE;
        }
        tab = t;
        scroll = 0;
        host.refresh();
    }

    // 左側のパーツのボタンから、パーツのタブを開く
    public void showPartsTab() {
        if (tab == RightTab.POSE) {
            CinemaState.leftRequest = CinemaState.LeftView.PARTS;   // タブは、そのまま。左のパネルだけ開く
            host.refresh();
        } else {
            selectTab(RightTab.POSE);
        }
    }
    // セクション同士の余白
    private static int sectionGap() {
        return Theme.u(9);
    }

    // セクションの中身の、左へのインデント
    private static int sectionIndent() {
        return Theme.u(4);
    }

    // セクションを組み立てる
    //   中身は、インデントして組み立てる
    //   畳んでいるときは、見出し以外を隠して、そのぶん詰める
    private int run(String key, IntUnaryOperator section, int y) {
        int itemsBefore = items.size();
        int labelsBefore = scrollLabels.size();

        currentKey = key;
        headerMade = false;
        x = baseX + sectionIndent();
        w = baseW - sectionIndent();
        int next = section.applyAsInt(y);
        x = baseX;
        w = baseW;
        currentKey = "";

        // 何も出さなかったセクションは、余白も足さない
        if (next <= y) return next;

        boolean hasHeader = items.size() > itemsBefore && items.get(itemsBefore).widget instanceof SectionHeader;
        if (hasHeader && isCollapsed(key)) {
            for (int i = itemsBefore + 1; i < items.size(); i++) items.get(i).hidden = true;
            scrollLabels.subList(labelsBefore, scrollLabels.size()).clear();
            next = y + headerH();   // 見出しの高さだけ残す
        }
        sectionSpans.add(new int[] {y, next});
        return next + sectionGap();
    }
    private List<Label> labels() {
        return fixedMode ? fixedLabels : scrollLabels;
    }

    // スクロールする部品として登録して、追加する(描画は、extractScrollItems が、切り取りながら行う)
    private <T extends AbstractWidget> T add(T widget) {
        host.addChild(widget);
        items.add(new Item(widget, widget.getY()));
        return widget;
    }

    private void label(String text, int lx, int ly, int color) {
        boolean heading = color == Theme.TEXT_DIM || color == Theme.SECTION;
        if (heading && !headerMade && !currentKey.isEmpty()) {
            headerMade = true;
            add(new SectionHeader(baseX, ly, baseW, currentKey, text, false));
            return;
        }
        scrollLabels.add(new Label(text, lx, ly, heading ? Theme.SECTION : color, heading));
    }

    private void note(String text, int lx, int ly) {
        scrollLabels.add(new Label(text, lx, ly, Theme.TEXT_NOTE));
    }

    private static void edit(UUID id, UnaryOperator<NpcData> fn) {
        NpcManager.get(id).ifPresent(cur -> NpcManager.replace(fn.apply(cur)));
    }

    // ================= タブ「基本」 =================
    private int buildMain(NpcData d, int y) {
        y = run("transform", yy -> sectionTransform(d, yy), y);
        y = run("playerTexts", yy -> sectionPlayerTexts(d, yy), y);
        y = run("modifier", yy -> sectionModifier(d, yy), y);
        y = run("pose", yy -> sectionPose(d, yy), y);
        y = run("animation", yy -> sectionAnimation(d, yy), y);
        y = run("equipment", yy -> sectionEquipment(d, yy), y);
        return y;
    }

    // ---- トランスフォーム(位置・回転) ----
    private static final String[] AXES = {"X", "Y", "Z"};
    private static final int[] AXIS_COLORS = {0xFFE5534B, 0xFF57AB5A, 0xFF539BF5};

    @FunctionalInterface
    private interface AxisConsumer {
        void accept(int axis, double value);
    }

    private int sectionTransform(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();
        label(Tr.get("cinema.ui.right.transform"), x, y, Theme.SECTION);
        y += lh;

        // 位置(X・Y・Z)
        note(Tr.get("cinema.ui.right.transform.position"), x, y);
        y += lay.th() + Theme.u(2);
        pos[0] = d.x();
        pos[1] = d.y();
        pos[2] = d.z();
        y = numberRow(y, pos, 0.05, 0.1, (axis, v) -> {
            pos[axis] = v;
            edit(id, cur -> cur.withTransform(pos[0], pos[1], pos[2], cur.yRot(), cur.xRot(), cur.headYRot(), cur.bodyYRot()));
        });
        y += lay.gap();

        // 回転
        y = rotationRows(d, y);

        // サイズ(生き物だけ)
        Entity live = NpcManager.entityOf(id);
        boolean living = live == null || live instanceof LivingEntity;
        if (living && !EntityProfiles.of(d.entityType()).hasNoScale()) {
            add(new ModernSlider(x, y, w, sh, Tr.get("cinema.ui.right.size"), "%", 10, 400, 5,
                    d.option(EntityFlags.SCALE, 100),
                    v -> edit(id, cur -> cur.withOption(EntityFlags.SCALE, (int) v))).setDefault(100));
            y += sh + lay.gap();
        }

        add(new ModernButton(x, y, w, lay.btnH(), Tr.get("cinema.ui.right.movetomyself"), ModernButton.Style.DEFAULT, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float faceYaw = mc.player.getYRot() + 180f;
            edit(id, cur -> cur.withTransform(mc.player.getX(), mc.player.getY(), mc.player.getZ(), faceYaw, cur.xRot(), faceYaw, faceYaw));
            host.refresh();   // 位置欄の表示も、新しい値にする
        }));
        return y + lay.btnH() + lay.gap();
    }

    // 全体の回転(X: 前後に倒す / Y: 体の向き / Z: 左右に倒す)。モデル全体が回る
    private int rotationRows(NpcData d, int y) {
        note(Tr.get("cinema.ui.right.transform.rotation"), x, y);
        y += lay.th() + Theme.u(2);
        double[] rotation = {d.option(EntityRotation.X, 0) / 100.0, Mth.wrapDegrees(d.bodyYRot()),
                d.option(EntityRotation.Z, 0) / 100.0};
        y = numberRow(y, rotation, 1.0, 1.0, (axis, v) -> {
            switch (axis) {
                case 0 -> edit(id, cur -> cur.withOption(EntityRotation.X, Math.round((float) (v * 100))));
                case 1 -> edit(id, cur -> {
                    // 体の向きを変えても、頭の左右のずれは、保つ
                    float body = (float) v;
                    float offset = Mth.clamp(Mth.wrapDegrees(cur.headYRot() - cur.bodyYRot()), -75f, 75f);
                    float head = body + offset;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, body);
                });
                default -> edit(id, cur -> cur.withOption(EntityRotation.Z, Math.round((float) (v * 100))));
            }
        });
        return y + lay.gap();
    }

    // X・Y・Z の3列の数値欄(枠の外の左上に、軸のラベル)。戻り値は、次の y
    private int numberRow(int y, double[] values, double dragPerPixel, double wheelStep, AxisConsumer onChange) {
        return numberRow(y, values, dragPerPixel, wheelStep, onChange, null);
    }

    // X・Y・Z の3列の数値欄(枠の外の左上に、軸のラベル)。out があれば、作った欄が入る。戻り値は、次の y
    private int numberRow(int y, double[] values, double dragPerPixel, double wheelStep, AxisConsumer onChange,
                          ModernNumberField[] out) {
        int labelH = lay.th() + Theme.u(2);
        int colGap = Math.max(2, Theme.u(4));
        int colW = (w - colGap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            int cx = x + i * (colW + colGap);
            labels().add(new Label(AXES[i], cx, y, AXIS_COLORS[i]));   // 固定の欄のときは、固定のラベルへ
            ModernNumberField field = add(new ModernNumberField(cx, y + labelH, colW, lay.sliderH(), "", AXIS_COLORS[i],
                    values[i], v -> onChange.accept(axis, v)).framed().dragSpeed(dragPerPixel, wheelStep));
            if (out != null) out[i] = field;
        }
        return y + labelH + lay.sliderH();
    }

    // ---- 修飾(状態 + エンティティ固有の設定) ----
    private int sectionModifier(NpcData d, int y) {
        Entity live = NpcManager.entityOf(id);
        boolean living = live == null || live instanceof LivingEntity;
        boolean canBaby = live != null && EntityReflect.hasMethodBefore(live, "setBaby", Mob.class);

        EntityProfile profile = EntityProfiles.of(d.entityType());
        List<String[]> flags = new ArrayList<>();
        if (!profile.isStateHidden(EntityFlags.FIRE) && !EntityFlags.isFireImmune(d.entityType())) {flags.add(new String[] {EntityFlags.FIRE, Tr.get("cinema.ui.right.fire")});}
        if (!profile.isStateHidden(EntityFlags.GLOW)) flags.add(new String[] {EntityFlags.GLOW, Tr.get("cinema.ui.right.glow")});
        if (canBaby) flags.add(new String[] {EntityFlags.BABY, Tr.get("cinema.ui.right.child")});
        if (living) flags.add(new String[] {EntityFlags.DAMAGE, Tr.get("cinema.ui.right.damage")});
        List<EntityOption> specs = profile.options();
        if (flags.isEmpty() && specs.isEmpty()) return y;

        label(Tr.get("cinema.ui.right.modifier"), x, y, Theme.SECTION);
        y += lay.th() + Theme.u(3);

        // 状態(縦に並べる)
        if(!flags.isEmpty()){
            note(Tr.get("cinema.ui.right.state"), x, y);
            y += lay.th();
        }
        for (String[] flag : flags) {
            String key = flag[0];
            add(new ModernCheckbox(x, y, w, lay.sliderH(), flag[1], d.option(key, 0) == 1,
                    on -> edit(id, cur -> cur.withOption(key, on ? 1 : 0))));//.setDefault(false));
            y += lay.sliderH();
        }

        // エンティティ固有の設定(色・種類など。プロフィールが宣言したものだけ)
        int sh = lay.sliderH();
        if (!specs.isEmpty()) {
            y += Theme.u(2);
            note(Tr.get("cinema.ui.right.entityoption"), x, y);
            y += lay.th();
        }
        for (EntityOption spec : specs) {
            if (spec.kind() == EntityOption.Kind.CHECKBOX) {
                add(new ModernCheckbox(x, y, w, sh, spec.label(), d.option(spec.key(), spec.def()) == 1,
                        on -> edit(id, cur -> cur.withOption(spec.key(), on ? 1 : 0)))
                );//.setDefault(spec.def() == 1));
            } else if (spec.kind() == EntityOption.Kind.DROPDOWN) {
                add(new ModernDropdown(x, y, w, sh, spec.label(), spec.names(), d.option(spec.key(), spec.def()),
                        i -> edit(id, cur -> cur.withOption(spec.key(), i)), host::onDropdownOpen)
                );//.setDefault(spec.def()));
            } else {
                ModernSlider slider = new ModernSlider(x, y, w, sh, spec.label(), "",
                        spec.min(), spec.max(), d.option(spec.key(), spec.def()),
                        v -> edit(id, cur -> cur.withOption(spec.key(), (int) v)));
                slider.setDefault(spec.def());
                if (!spec.names().isEmpty()) {
                    slider.setValueFormatter(v -> {
                        int i = Math.max(0, Math.min(spec.names().size() - 1, (int) Math.round(v)));
                        return spec.names().get(i);
                    });
                }
                add(slider);
            }
            y += sh;
        }
        return y + lay.gap();
    }

    // ---- 姿勢・モデル ----
    private int sectionPose(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int pgap = Math.max(1, Theme.u(3));

        // 姿勢: モブごとに、意味のあるものだけを出す(選べるものがなければ、欄ごと出さない)
        List<NpcPose> poses = NpcData.PLAYER.equals(d.kind())
                ? NpcPose.PLAYER : EntityProfiles.of(d.entityType()).poses();
        if (poses.size() > 1) {
            label(Tr.get("cinema.ui.right.pose"), x, y, Theme.SECTION);
            y += lh;
            int pw = (w - pgap * (poses.size() - 1)) / poses.size();
            for (int i = 0; i < poses.size(); i++) {
                NpcPose p = poses.get(i);
                add(new ModernButton(x + i * (pw + pgap), y, pw, lay.btnH(), p.label, ModernButton.Style.DEFAULT, () -> {
                    edit(id, cur -> cur.withPose(p));
                    host.refresh();
                }).setSelected(d.npcPose() == p));
            }
            y += lay.btnH() + lay.gap();
        }

        // モデル(腕の太さ): プレイヤーのみ
        if (NpcData.PLAYER.equals(d.kind())) {
            label(Tr.get("cinema.ui.right.model"), x, y, Theme.SECTION);
            y += lh;
            String[] modelLabels = {Tr.get("cinema.ui.right.model.auto"), Tr.get("cinema.ui.right.model.normal"),
                    Tr.get("cinema.ui.right.model.slim")};
            String[] modelValues = {null, "wide", "slim"};
            int mw = (w - pgap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                String value = modelValues[i];
                add(new ModernButton(x + i * (mw + pgap), y, mw, lay.btnH(), modelLabels[i],
                        ModernButton.Style.DEFAULT, () -> {
                    edit(id, cur -> cur.withModel(Optional.ofNullable(value)));
                    host.refresh();
                }).setSelected(Objects.equals(d.model().orElse(null), value)));
            }
            y += lay.btnH() + lay.gap();
        }
        return y;
    }

    // ---- アニメーション(時間・歩行・腕) ----
    private int sectionAnimation(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();

        Entity live = NpcManager.entityOf(id);
        boolean living = live == null || live instanceof LivingEntity;   // エンドクリスタルやボートは、生き物ではない
        boolean isPlayer = NpcData.PLAYER.equals(d.kind());
        boolean showWalk = living && (isPlayer || EntityProfiles.of(d.entityType()).walks());
        boolean showArms = living && (isPlayer || EntityProfiles.of(d.entityType()).isHumanoid());

        label(Tr.get("cinema.ui.right.animation"), x, y, Theme.SECTION);
        y += lh;

        // 時間
        add(new ModernSlider(x, y, w, sh, Tr.get("cinema.ui.right.animation.frame"), "tick", 0,
                EntityTweaks.animTickMax(d.entityType()), 0.1, d.animTickOrDefault(),
                v -> edit(id, cur -> cur.withAnimTick((float) v)))
                .setDefault(EntityTweaks.defaultAnimTick(d.entityType())));
        y += sh;

        // 歩行
        if (showWalk) {
            add(new ModernSlider(x, y, w, sh, Tr.get("cinema.ui.right.movement.walk"), "%", -100, 100,
                    d.option(EntityMotion.WALK, 0), v -> edit(id, cur -> cur.withOption(EntityMotion.WALK, (int) v)))
                    .setDefault(0));
            y += sh;
        }

        // 腕(構える・振る)
        if (showArms) {
            add(new ModernCheckbox(x, y, w, sh, Tr.get("cinema.ui.right.movement.ready"),
                    d.option(EntityMotion.ARMS, 0) == 1,
                    on -> edit(id, cur -> cur.withOption(EntityMotion.ARMS, on ? 1 : 0))));//.setDefault(false));
            y += sh;
            // 古い保存データ(ARMS が 2〜4)は、昔の固定値で読み替える
            int legacy = Math.round(EntityMotion.legacySwing(d.option(EntityMotion.ARMS, 0)) * 100f);
            add(new ModernSlider(x, y, w, sh, Tr.get("cinema.ui.right.movement.swing"), "%", 0, 100, 1,
                    d.option(EntityMotion.SWING, legacy),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.SWING, (int) v))).setDefault(0));
            y += sh;
        }
        return y + lay.gap();
    }

    // ---- 装備 ----
    private int sectionEquipment(NpcData d, int y) {
        EntityProfile profile = EntityProfiles.of(d.entityType());
        if (profile.equipmentMode() == EquipmentMode.NONE) return y;   // 装備欄なし(手の動作も出さない)

        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();

        // スロットは、クリックで外す。右クリックで装飾(エンチャント光沢・トリム)。入れるのは、インベントリの画面から
        label(Theme.fit(host.font(), Tr.get("cinema.ui.right.equipment"), w), x, y, Theme.SECTION);
        y += lh;

        // メインハンドだけの装備欄(落下ブロック・額縁など)
        if (profile.isMainHandOnly()) return sectionHeldItem(d, y, profile.heldItemLabelOrDefault());

        // 6スロットを、1行に並べる(メイン・オフ・頭・胴・脚・足)
        boolean animalGear = profile.hasAnimalGear();
        int cgap = Math.max(1, Theme.u(2));
        int cw = (w - cgap * 5) / 6;
        int ch = Math.max(Theme.u(26), 26);
        EquipmentSlot[] order = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < order.length; i++) {
            addSlotButton(d, order[i], x + i * (cw + cgap), y, cw, ch, animalGear);
        }
        y += ch + lay.gap();

        // 手の動作(使えるアイテムを持っているときだけ)
        ItemStack mainItem = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);
        ItemStack offItem = EquipmentEdit.stackOf(d, EquipmentSlot.OFFHAND);
        boolean anyMotion = EntityMotion.isSpear(mainItem) || hasUseMotion(mainItem) || hasUseMotion(offItem)
                || mainItem.is(Items.CROSSBOW) || offItem.is(Items.CROSSBOW);
        if (anyMotion) {
            // 同じセクションの中の2つ目の見出し(畳める部品にはならない)
            label("手の動作", x, y, Theme.SECTION);
            y += lh;
        }

        if (EntityMotion.isSpear(mainItem)) {
            // 槍: 突き(ジャブ)と突進(チャージ)は、別の動作。どちらの進み具合も、同じスライダーで決める
            add(new ModernDropdown(x, y, w, sh, "槍の攻撃", List.of(EntityMotion.SPEAR_NAMES),
                    d.option(EntityMotion.SPEAR, 0),
                    i -> edit(id, cur -> cur.withOption(EntityMotion.SPEAR, i)), host::onDropdownOpen));
                    //.setDefault(0));
            y += sh;
            add(new ModernSlider(x, y, w, sh, "槍の進み具合", "%", 0, 100, d.option(EntityMotion.SPEAR_AMOUNT, 50),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.SPEAR_AMOUNT, (int) v))).setDefault(50));
            y += sh;
        } else if (hasUseMotion(mainItem)) {
            add(new ModernSlider(x, y, w, sh, "メイン 使用モーション", "%", 0, 100,
                    d.option(EntityMotion.USE_MAIN, EntityMotion.defaultUse(mainItem)),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.USE_MAIN, (int) v)))
                    .setDefault(EntityMotion.defaultUse(mainItem)));
            y += sh;
        }
        if (hasUseMotion(offItem)) {
            add(new ModernSlider(x, y, w, sh, "オフ 使用モーション", "%", 0, 100,
                    d.option(EntityMotion.USE_OFF, EntityMotion.defaultUse(offItem)),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.USE_OFF, (int) v)))
                    .setDefault(EntityMotion.defaultUse(offItem)));
            y += sh;
        }
        if (mainItem.is(Items.CROSSBOW) || offItem.is(Items.CROSSBOW)) {
            add(new ModernCheckbox(x, y, w, sh, "装填済み(矢をつがえて構える)", d.option(EntityMotion.LOADED, 0) == 1,
                    on -> edit(id, cur -> cur.withOption(EntityMotion.LOADED, on ? 1 : 0))));//.setDefault(false));
            y += sh;
        }

        statusY = y + Theme.u(2);   // メッセージの行
        return y + lay.th() + Theme.u(6);
    }

    // 持ち物がそのまま内容物のエンティティ(落下ブロック・額縁): メインハンドのスロットだけ(中央に置く)
    private int sectionHeldItem(NpcData d, int y, String heldLabel) {
        // 見出しは、すでに上にあるので、ここは説明文にする
        note(Theme.fit(host.font(), heldLabel + "(左:開く Shift:外す)", w), x, y);
        y += lay.th() + Theme.u(3);

        int cgap = Math.max(1, Theme.u(3));
        int cw = (w - cgap * 3) / 4;   // 通常の装備の1マスと同じ大きさ
        int ch = Math.max(Theme.u(26), 26);
        ItemStack stack = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);

        UUID npcId = d.id();
        ItemButton slot = new ItemButton(x + (w - cw) / 2, y, cw, ch, stack,
                EquipmentEdit.ghostOf(EquipmentSlot.MAINHAND), b -> {
            ItemStack current = currentStack(npcId, EquipmentSlot.MAINHAND);   // クリック時点の最新の装備
            if (Modifiers.shift()) {   // Shift+クリック(左右とも): 外す
                if (!current.isEmpty()) {
                    EquipmentEdit.set(npcId, EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    host.refresh();
                }
                return;
            }
            if (b == 0) openInventory();
            else say("このエンティティに装飾はありません");
        });
        slot.tip(stack.isEmpty() ? heldLabel + "(左クリック: インベントリ)"
                : heldLabel + ": " + stack.getHoverName().getString() + "(Shift: 外す)");
        add(slot);
        y += ch + lay.gap();

        statusY = y + Theme.u(2);
        return y + lay.th() + Theme.u(6);
    }

    // 使用モーション(弓・クロスボウ・トライデント・盾・食べ物など)があるアイテムか
    private static boolean hasUseMotion(ItemStack stack) {
        return !stack.isEmpty() && !"NONE".equals(stack.getUseAnimation().name());
    }

    // クリエ画面を、ゲームモード・OP に関係なく開く(サーバーへは何も送らない)
    private static void openInventory() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        mc.gui.setScreen(new CinemaCreativeScreen(player, player.connection.enabledFeatures(), true));
        UiSounds.inventory();
    }

    private void addSlotButton(NpcData d, EquipmentSlot slot, int bx, int by, int bw, int bh, boolean animalGear) {
        UUID npcId = d.id();
        ItemStack stack = EquipmentEdit.stackOf(d, slot);   // 表示用(ツールチップ)。クリックでは、最新を取り直す
        String slotName = EquipmentEdit.label(slot, animalGear);
        ItemButton button = new ItemButton(bx, by, bw, bh, stack, EquipmentEdit.ghostOf(slot), b -> {
            ItemStack current = currentStack(npcId, slot);
            if (Modifiers.shift()) {   // Shift+クリック(左右とも): 外す
                if (!current.isEmpty()) {
                    EquipmentEdit.set(npcId, slot, ItemStack.EMPTY);
                    host.refresh();
                }
                return;
            }
            if (b == 0) {   // 左クリック: インベントリを開く
                openInventory();
            } else if (current.isEmpty()) {   // 右クリック: 装飾(空のときは、できない)
                say(slotName + "は空です(左クリックでインベントリを開けます)");
            } else {
                Minecraft.getInstance().gui.setScreen(new EquipmentMenuScreen(host.screen(), slotName,
                        () -> currentStack(npcId, slot),
                        s -> EquipmentEdit.set(npcId, slot, s)));
            }
        });
        button.tip(stack.isEmpty() ? slotName + "(左クリック: インベントリ)" : slotName + ": " + stack.getHoverName().getString() + "(右: 装飾 / Shift: 外す)");
        add(button);
    }

    private void say(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
        UiSounds.deny();
    }

    // ---- プレイヤーのネームタグ・ケープの文字(プレイヤーだけ) ----
    private int sectionPlayerTexts(NpcData d, int y) {
        if (!NpcData.PLAYER.equals(d.kind())) return y;
        int lh = lay.th() + Theme.u(3);

        label("ネームタグ・ケープ", x, y, Theme.SECTION);
        y += lh;

        // 表示名(空欄: ユーザー名)
        ModernTextField nameField = new ModernTextField(x, y, w, lay.btnH());
        nameField.setHint("表示名(空欄 = ユーザー名)");
        nameField.setMaxLength(48);
        nameField.setValue(NpcTexts.get(id, NpcTexts.NAME_TAG));
        nameField.setOnChange(s -> {
            NpcTexts.set(id, NpcTexts.NAME_TAG, s);
            edit(id, cur -> cur);   // 反映し直す
        });
        add(nameField);
        y += lay.btnH() + lay.gap();

        // BelowName(名前の下に出る文字)
        ModernTextField belowField = new ModernTextField(x, y, w, lay.btnH());
        belowField.setHint("BelowName(名前の下の文字)");
        belowField.setMaxLength(48);
        belowField.setValue(NpcTexts.get(id, NpcTexts.BELOW_NAME));
        belowField.setOnChange(s -> {
            NpcTexts.set(id, NpcTexts.BELOW_NAME, s);
            edit(id, cur -> cur);
        });
        add(belowField);
        y += lay.btnH() + lay.gap();

// ケープ: 画像と名前から選ぶ画面を開く
        String capeName = NpcTexts.get(id, NpcTexts.CAPE_FILE);
        add(new ModernButton(x, y, w, lay.btnH(), "ケープ: " + CapeLibrary.labelOf(capeName),
                ModernButton.Style.DEFAULT, () -> Minecraft.getInstance().gui.setScreen(
                new CapeScreen(host.screen(), capeName, picked -> {
                    NpcTexts.set(id, NpcTexts.CAPE_FILE, picked);
                    edit(id, cur -> cur);
                }))));
        y += lay.btnH() + lay.gap();
        return y;
    }

    // ================= タブ「パーツ」 =================

    // 全体の回転と、選んだパーツの調整(パーツの一覧は、左パネルにある)
    private int buildParts(NpcData d, int y) {
        Entity live = NpcManager.entityOf(id);
        String selected = PartSelection.selected();

        // 選んでいたパーツが、このモブに無ければ、選択を外す
        if (selected != null && !selected.startsWith("@")) {
            List<ModelPartTree.Node> nodes = live == null ? List.of() : ModelPartTree.of(live);
            if (nodes.stream().noneMatch(n -> n.path().equals(selected))) PartSelection.select(null);
        }

        y = run("rotation", yy -> sectionRotation(d, yy), y);
        y = run("part", yy -> sectionPartEditor(d, yy), y);
        return y;
    }

    // 全体の回転(基本タブと同じ欄)
    private int sectionRotation(NpcData d, int y) {
        label(Tr.get("cinema.ui.parts.rotation"), x, y, Theme.SECTION);
        y += headerH();
        return rotationRows(d, y);
    }

    private void setChannel(String path, PartEdits.Channel channel, double value) {
        PartGlow.select(path);
        edit(id, cur -> cur.withOption(PartEdits.key(path, channel), PartEdits.encode(value)));
    }

    private static double[] channelValues(NpcData d, String path, PartEdits.Channel a, PartEdits.Channel b, PartEdits.Channel c) {
        return new double[] {PartEdits.get(d, path, a), PartEdits.get(d, path, b), PartEdits.get(d, path, c)};
    }

    // 選んでいるパーツの調整(表示・位置・回転・大きさ)
    private int sectionPartEditor(NpcData d, int y) {
        String path = PartSelection.selected();
        int sh = lay.sliderH();

        if (path == null) {
            label(Tr.get("cinema.ui.parts.editor"), x, y, Theme.SECTION);
            y += headerH();
            note(Theme.fit(host.font(), Tr.get("cinema.ui.parts.select_hint"), w), x, y);
            return y + lay.th() + Theme.u(3);
        }

        label(ModelPartTree.label(ModelPartTree.lastName(path)), x, y, Theme.SECTION);
        y += headerH();

        // 表示とリセットを、1行に
        int half = (w - lay.gap()) / 2;
        add(new ModernCheckbox(x, y, half, sh, Tr.get("cinema.ui.parts.visible"),
                PartEdits.get(d, path, PartEdits.Channel.VISIBLE) >= 0.5,
                on -> setChannel(path, PartEdits.Channel.VISIBLE, on ? 1 : 0)));
        add(new ModernButton(x + half + lay.gap(), y, w - half - lay.gap(), sh, Tr.get("cinema.ui.parts.reset"),
                ModernButton.Style.DEFAULT, () -> {
            edit(id, cur -> PartEdits.reset(cur, path));
            host.refresh();
        }));
        y += sh + lay.gap();

        // 位置のずれ(ピクセル)
        note(Tr.get("cinema.ui.parts.position"), x, y);
        y += lay.th() + Theme.u(2);
        y = numberRow(y, channelValues(d, path, PartEdits.Channel.PX, PartEdits.Channel.PY, PartEdits.Channel.PZ),
                0.1, 0.5, (axis, v) -> setChannel(path, PartEdits.Channel.position(axis), v));
        y += lay.gap();

        // 回転(足し算。度)
        note(Tr.get("cinema.ui.parts.rotation.add"), x, y);
        y += lay.th() + Theme.u(2);
        y = numberRow(y, channelValues(d, path, PartEdits.Channel.RX, PartEdits.Channel.RY, PartEdits.Channel.RZ),
                1.0, 1.0, (axis, v) -> setChannel(path, PartEdits.Channel.rotation(axis), v));
        y += lay.gap();

        // 大きさ(%)
// 大きさ(%): 位置・回転と同じ XYZ。右上のリンクがオンなら、1つ変えると、3軸が割合を保ったまま変わる
        int linkSize = lay.th() + Theme.u(6);
        note(Tr.get("cinema.ui.parts.scale"), x, y + (linkSize - lay.th()) / 2);
        add(new LinkButton(x + w - linkSize, y, linkSize, linkSize,
                () -> PartGizmo.scaleLinked, on -> PartGizmo.scaleLinked = on));
        y += linkSize + Theme.u(2);
        ModernNumberField[] scaleFields = new ModernNumberField[3];
        y = numberRow(y, channelValues(d, path, PartEdits.Channel.SX, PartEdits.Channel.SY, PartEdits.Channel.SZ),
                1.0, 5.0, (axis, v) -> setScale(path, axis, v, scaleFields), scaleFields);
        y += lay.gap();

        // [DEV] つながりの確認(描画のフック・軸・選択・光る対象)
        if (CinemaState.devMode) {
            note(Theme.fit(host.font(), "hook=" + PartEdits.applied + " axes=" + PartAxes.hasData(path)
                    + " glow=" + PartGlow.current(), w), x, y);
            y += lay.th() + Theme.u(3);
        }
        return y;
    }

    // 大きさの入力。リンクがオンなら、変えた軸の変化の割合を、ほかの軸にもかける(3軸の割合を保つ)
    private void setScale(String path, int axis, double value, ModernNumberField[] fields) {
        NpcData data = NpcManager.get(id).orElse(null);
        if (!PartGizmo.scaleLinked || data == null) {
            setChannel(path, PartEdits.Channel.scale(axis), value);
            return;
        }
        double old = PartEdits.get(data, path, PartEdits.Channel.scale(axis));
        for (int a = 0; a < 3; a++) {
            double next = a == axis ? value
                    : old == 0 ? value : PartEdits.get(data, path, PartEdits.Channel.scale(a)) * value / old;
            setChannel(path, PartEdits.Channel.scale(a), next);
            if (a != axis && fields[a] != null) fields[a].setValueSilently(next);   // 表示だけ合わせる
        }
    }

    // 畳んでいるか(基本タブのセクションだけ)
    private static boolean isCollapsed(String key) {
        return collapsed.contains(key);
    }
    private static void toggleSection(String key) {
        if (!collapsed.remove(key)) collapsed.add(key);
    }
    private int headerH() {
        return lay.th() + Theme.u(3);
    }

    // スクロール
    private void applyScroll() {
        for (Item it : items) {
            AbstractWidget widget = it.widget;
            widget.setY(it.baseY - scroll);
            // 表示範囲に、少しでも掛かっていれば表示する(はみ出した部分は、描くときに切り取る)。畳んだ中身は、常に隠す
            boolean overlaps = widget.getY() + widget.getHeight() > contentTop && widget.getY() < contentBottom;
            boolean show = !it.hidden && overlaps;
            widget.visible = show;
            widget.active = show;
        }
    }
    private void setScroll(int value) {
        scroll = Mth.clamp(value, 0, maxScroll);
        applyScroll();
    }
    public boolean contains(double mx, double my) {
        return mx >= lay.rightX() && mx < lay.rightX() + lay.panelW()
                && my >= lay.panelY() && my < lay.panelY() + lay.panelH();
    }

    // ホイール
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!contains(mx, my) || maxScroll <= 0) return false;
        setScroll(scroll - (int) Math.round(scrollY * Theme.u(24)));
        wheelLockUntil = System.currentTimeMillis() + 400;
        return true;
    }
    public boolean wheelLocked(double mx, double my) {
        return System.currentTimeMillis() < wheelLockUntil && contains(mx, my);
    }
    public boolean beginDragScroll(double mx, double my) {
        if (maxScroll <= 0 || mx < lay.rightX() || mx >= lay.rightX() + lay.panelW()
                || my < contentTop || my >= contentBottom) {
            return false;
        }
        dragScrolling = true;
        return true;
    }
    public boolean isDragScrolling() {
        return dragScrolling;
    }
    public void dragScrollBy(double dy) {
        setScroll(scroll - (int) Math.round(dy));
        wheelLockUntil = System.currentTimeMillis() + 400;
    }
    public void endDragScroll() {
        dragScrolling = false;
    }

    // スクロールする部品を、表示範囲で切り取って描く(範囲の外にはみ出した部分は、見えない)
    public void extractScrollItems(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // 範囲の外に、カーソルがあるときは、ホバーの表示を出さない
        int hoverY = my >= contentTop && my < contentBottom ? my : -10000;
        g.enableScissor(lay.rightX(), contentTop, lay.rightX() + lay.panelW(), contentBottom);
        for (Item it : items) {
            if (it.widget.visible) it.widget.extractRenderState(g, mx, hoverY, delta);
        }
        g.disableScissor();
    }
    // スクロールの表示範囲の外(上のタブの行・下の余白)か(パネルの中で)
    public boolean outsideScrollBand(double mx, double my) {
        return contains(mx, my) && (my < contentTop || my >= contentBottom);
    }
    // スクロールする部品の、押せる・押せないを、まとめて切り替える
    public void setScrollItemsActive(boolean on) {
        for (Item it : items) it.widget.active = on && it.widget.visible;
    }

    // ==描画==
    // 右端のスクロールバー(つまみをドラッグできる)
    private final class ScrollBar extends AbstractWidget implements Draggable {
        ScrollBar(int bx, int by, int bw, int bh) {
            super(bx, by, bw, bh, Component.empty());
        }

        private int thumbH() {
            int view = contentBottom - contentTop;
            int total = view + maxScroll;
            return Math.max(Theme.u(16), getHeight() * view / Math.max(1, total));
        }

        private int thumbY() {
            int free = getHeight() - thumbH();
            return getY() + (maxScroll == 0 ? 0 : free * scroll / maxScroll);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            if (maxScroll <= 0) return;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x22FFFFFF);
            g.fill(getX(), thumbY(), getX() + getWidth(), thumbY() + thumbH(), 0x88FFFFFF);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return maxScroll > 0 && Theme.isClick(event.button()) && isMouseOver(event.x(), event.y());
        }

        @Override
        public void dragBy(double dx, double dy) {
            int free = getHeight() - thumbH();
            if (free > 0) setScroll(scroll + (int) Math.round(dy * maxScroll / free));
        }

        @Override
        public void dragEnd(double mouseX, double mouseY) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    // クリックで畳める見出し(▼ 開いている / ▶ 畳んでいる)。右に、線を引く。edited なら、黄色
    private final class SectionHeader extends AbstractWidget {
        private final String key;
        private final String text;
        private final boolean edited;
        private float hover;

        SectionHeader(int hx, int hy, int hw, String key, String text, boolean edited) {
            super(hx, hy, hw, headerH(), Component.literal(text));
            this.key = key;
            this.text = text;
            this.edited = edited;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            Font font = host.font();
            boolean hov = active && isMouseOver(mx, my);
            hover += ((hov ? 1f : 0f) - hover) * 0.35f;
            int color = Theme.lerp(edited ? Theme.CHANGED : Theme.SECTION, 0xFFFFFFFF, hover * 0.5f);

            // 背の高い見出しは、文字を縦の中央に置く
            int top = getY() + (getHeight() - lay.th() - Theme.u(3)) / 2;
            int headH = lay.th();
            int s = Math.max(3, Theme.u(4));
            triangle(g, getX() + s, top + headH / 2, s, !isCollapsed(key), color);

            int tx = getX() + s * 2 + Theme.u(4);
            String shown = Theme.fit(font, text, getWidth() - (tx - getX()));
            Theme.text(g, font, shown, tx, top, color);
            int lineX = tx + Theme.textWidth(font, shown) + Theme.u(6);
            int lineEnd = getX() + getWidth();
            if (lineEnd - lineX > Theme.u(12)) {
                int lineY = top + headH / 2;
                g.fill(lineX, lineY, lineEnd, lineY + 1, Theme.SECTION_LINE);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
            toggleSection(key);
            UiSounds.tab();
            host.refresh();
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    // モブ名の上に、IDを出すための、見えない部品(クリックは、受けない)
    private static final class NameTip extends AbstractWidget {
        private final String text;

        NameTip(int nx, int ny, int nw, int nh, String text) {
            super(nx, ny, nw, nh, Component.empty());
            this.text = text;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            if (active && isMouseOver(mx, my)) Tooltips.request(this, text);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    // 小さな三角(down が true なら▼、false なら▶)
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

    // パネルの背景と、文字(ウィジェットの前に呼ぶ)
    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        Theme.panel(g, backX, backY, backW, backH);   // 戻るボタンの背景
        NpcManager.get(id).ifPresent(d -> NpcFaces.paint(g, d, iconX, iconY, iconSize));
        // 固定のラベル
        for (Label l : fixedLabels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }

        // パーツのタブ: 下半分(値の設定)の区切り線と背景
        if (tab == RightTab.POSE) {
            int lineY = editorTop - Theme.u(2);
            g.fill(lay.rightX() + lay.pad(), lineY, lay.rightX() + lay.panelW() - lay.pad(), lineY + 1, Theme.SECTION_LINE);
            Theme.roundRect(g, baseX - Theme.u(3), editorTop, baseW + Theme.u(6), panelBottom - editorTop, Theme.SECTION_CARD);
        }

        int padV = Theme.u(2);
        int padH = Theme.u(3);
        g.enableScissor(lay.rightX(), contentTop, lay.rightX() + lay.panelW(), contentBottom);
        for (int[] span : sectionSpans) {
            int top = span[0] - scroll - padV;
            int bottom = span[1] - scroll;
            Theme.roundRect(g, baseX - padH, top, baseW + padH * 2, bottom - top, Theme.SECTION_CARD);
        }
        g.disableScissor();

        // スクロールするラベル(表示範囲で切り取る)
        g.enableScissor(lay.rightX(), contentTop, lay.rightX() + lay.panelW(), contentBottom);
        for (Label l : scrollLabels) {
            int ly = l.y() - scroll;
            if (ly + lay.th() * 2 < contentTop || ly > contentBottom) continue;
            // すべてのラベルを描画
            Theme.text(g, host.font(), l.text(), l.x(), ly, l.color());
            // 見出しの場合だけ、右側に区切り線を描画
            if (l.heading()) {
                int lineX = l.x() + Theme.textWidth(host.font(), l.text()) + Theme.u(6);
                int lineEnd = baseX + baseW;
                if (lineEnd - lineX > Theme.u(12)) {
                    int lineY = ly + lay.th() / 2;
                    g.fill(lineX, lineY, lineEnd, lineY + 1, Theme.SECTION_LINE);
                }
            }
        }
        if (tab == RightTab.MAIN && System.currentTimeMillis() < statusUntil) {
            Theme.text(g, host.font(), Theme.fit(host.font(), status, w), x, statusY - scroll, 0xFFE5A04B);
        }
        g.disableScissor();
    }

    // 最新の装備を取得する(クリックの時点で、データが変わっていても、安全なように)
    private static ItemStack currentStack(UUID npcId, EquipmentSlot slot) {
        return NpcManager.get(npcId).map(cur -> EquipmentEdit.stackOf(cur, slot)).orElse(ItemStack.EMPTY);
    }

    // ---- 本体のパーツを、画面のドラッグで動かす ----
    private final PartGizmo.Target gizmoTarget = new PartGizmo.Target() {
        @Override
        public double get(PartEdits.Channel channel) {
            return NpcManager.get(id).map(d -> PartEdits.get(d, PartSelection.selected(), channel)).orElse(channel.def);
        }

        @Override
        public void set(PartEdits.Channel channel, double value) {
            setChannel(PartSelection.selected(), channel, value);
        }
    };
    public boolean gizmoReady() {
        return tab == RightTab.POSE && PartSelection.selected() != null;
    }
    public void gizmoHover(double mx, double my, boolean overWorld) {
        if (!gizmoReady()) {
            PartAxes.hovered = -1;
            return;
        }
        PartGizmo.hover(PartSelection.selected(), mx, my, overWorld);
    }
    public boolean gizmoPress(double mx, double my) {
        return gizmoReady() && PartGizmo.press(PartSelection.selected(), mx, my);
    }
    public boolean gizmoDragging() {
        return PartGizmo.dragging();
    }
    public void gizmoDrag(double dx, double dy) {
        PartGizmo.drag(dx, dy, gizmoTarget);
    }
    public void gizmoRelease() {
        PartGizmo.release();
        host.refresh();
    }
    public void gizmoOverlay(GuiGraphicsExtractor g, int mx, int my) {
        PartGizmo.drawOverlay(g, host.font(), mx, my);
    }
}