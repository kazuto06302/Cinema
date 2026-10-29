package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityFlags;
import net.kztmc.mc.cinema.entity.EntityMotion;
import net.kztmc.mc.cinema.entity.EntityOption;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.EntityTweaks;
import net.kztmc.mc.cinema.entity.profile.AnimationModeProfile;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.npc.NpcPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 右パネル: 選択中のNPCの編集。
 *   最上段: 名前と削除
 *   タブ  : [基本(2) | ポーズ(1)]
 *   基本  : 位置・向き・状態(燃える/光る/子ども/ダメージ)・姿勢・モデル・アニメーション・動き・固有オプション・装備・手の動作
 *   ポーズ: パーツごとのポーズ(翼・頭など)。これから追加していく
 * 基本の中身は長いので、マウスホイールと右端のスクロールバーで、上下にスクロールできる。
 */
public final class RightPanel {
    private enum RightTab {
        MAIN("基本"), POSE("ポーズ");

        final String label;

        RightTab(String label) {
            this.label = label;
        }
    }

    private record Label(String text, int x, int y, int color) {}

    private record Item(AbstractWidget widget, int baseY) {}

    private static RightTab tab = RightTab.MAIN;
    private static int scroll;   // 作り直しても保持する

    private final List<Label> fixedLabels = new ArrayList<>();    // スクロールしない(名前)
    private final List<Label> scrollLabels = new ArrayList<>();   // スクロールする
    private final List<Item> items = new ArrayList<>();           // スクロールするウィジェット

    private Layout lay;
    private PanelHost host;
    private UUID id;
    private int x, w, contentTop, contentBottom, maxScroll;
    private final double[] pos = new double[3];

    private String status = "";
    private long statusUntil;
    private int statusY;

    // ================= 組み立て =================

    public void build(Layout lay, PanelHost host, NpcData d) {
        this.lay = lay;
        this.host = host;
        this.id = d.id();
        fixedLabels.clear();
        scrollLabels.clear();
        items.clear();

        x = lay.rightX() + lay.pad();
        w = lay.panelW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);

        // --- 最上段: 名前(左)と、削除(右) ---
        String name = NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType());
        int delW = Theme.u(44);
        fixedLabels.add(new Label(Theme.fit(host.font(), name, w - delW - lay.gap()), x,
                y + (lay.btnH() - lay.th()) / 2, Theme.TEXT));
        UUID npcId = id;
        host.add(new ModernButton(x + w - delW, y, delW, lay.btnH(), "削除", ModernButton.Style.DANGER, () -> {
            NpcManager.remove(npcId);
            host.refresh();
        }));
        y += lay.btnH() + Theme.u(5);

        // --- タブ行(2:1、角丸なし) ---
        int mainW = w * 2 / 3;
        host.add(new TabButton(x, y, mainW, lay.btnH(), RightTab.MAIN.label, () -> selectTab(RightTab.MAIN))
                .setSelected(tab == RightTab.MAIN));
        host.add(new TabButton(x + mainW, y, w - mainW, lay.btnH(), RightTab.POSE.label, () -> selectTab(RightTab.POSE))
                .setSelected(tab == RightTab.POSE));
        y += lay.btnH() + lay.gap();

        contentTop = y;
        contentBottom = lay.panelY() + lay.panelH() - lay.pad();

        // スクロールバーの分だけ、中身の幅を狭くする
        int barW = Theme.u(5);
        int fullW = w;
        w = fullW - barW - Theme.u(2);

        int end;
        if (tab == RightTab.MAIN) {
            end = buildMain(d, y);
        } else {
            end = buildPose(y);
        }

        maxScroll = Math.max(0, end - contentBottom);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        host.add(new ScrollBar(x + fullW - barW, contentTop, barW, contentBottom - contentTop));
        w = fullW;
        applyScroll();
    }

    private void selectTab(RightTab t) {
        tab = t;
        scroll = 0;
        host.refresh();
    }

    /** スクロールする部品として登録して、追加する */
    private <T extends AbstractWidget> T add(T widget) {
        host.add(widget);
        items.add(new Item(widget, widget.getY()));
        return widget;
    }

    private void label(String text, int lx, int ly, int color) {
        scrollLabels.add(new Label(text, lx, ly, color));
    }

    private static void edit(UUID id, UnaryOperator<NpcData> fn) {
        NpcManager.get(id).ifPresent(cur -> NpcManager.replace(fn.apply(cur)));
    }

    // ================= タブ「基本」 =================

    private int buildMain(NpcData d, int y) {
        y = sectionPosition(d, y);
        y = sectionOrientation(d, y);
        y = sectionStates(y);
        y = sectionPosture(d, y);
        y = sectionAnimation(d, y);
        y = sectionMotion(d, y);
        y = sectionOptions(d, y);
        y = sectionEquipment(d, y);
        y = sectionDev(d, y);
        return y;
    }

    // ---- 位置 ----

    private int sectionPosition(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        label("位置", x, y, Theme.TEXT_DIM);
        y += lh;

        pos[0] = d.x();
        pos[1] = d.y();
        pos[2] = d.z();
        String[] axisNames = {"X", "Y", "Z"};
        int[] axisColors = {0xFFE5534B, 0xFF57AB5A, 0xFF539BF5};
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            add(new ModernNumberField(x, y, w, lay.sliderH(), axisNames[i], axisColors[i], pos[i], v -> {
                pos[axis] = v;
                edit(id, cur -> cur.withTransform(pos[0], pos[1], pos[2],
                        cur.yRot(), cur.xRot(), cur.headYRot(), cur.bodyYRot()));
            }));
            y += lay.sliderH();
        }
        add(new ModernButton(x, y, w, lay.btnH(), "自分の前に移動", ModernButton.Style.DEFAULT, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float faceYaw = mc.player.getYRot() + 180f;
            edit(id, cur -> cur.withTransform(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    faceYaw, cur.xRot(), faceYaw, faceYaw));
            host.refresh();   // 位置欄の表示も、新しい値にする
        }));
        return y + lay.btnH() + lay.gap();
    }

    // ---- 向き ----

    private int sectionOrientation(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();
        label("向き", x, y, Theme.TEXT_DIM);
        y += lh;

        float body = Mth.wrapDegrees(d.bodyYRot());
        float headOffset = Mth.clamp(Mth.wrapDegrees(d.headYRot() - d.bodyYRot()), -75f, 75f);

        add(new ModernSlider(x, y, w, sh, "体の向き", "°", -180, 180, body, v ->
                edit(id, cur -> {
                    float bo = (float) v;
                    float off = Mth.clamp(Mth.wrapDegrees(cur.headYRot() - cur.bodyYRot()), -75f, 75f);
                    float head = bo + off;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, bo);
                })));
        y += sh;
        add(new ModernSlider(x, y, w, sh, "頭の左右", "°", -75, 75, headOffset, v ->
                edit(id, cur -> {
                    float head = cur.bodyYRot() + (float) v;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, cur.bodyYRot());
                })));
        y += sh;
        add(new ModernSlider(x, y, w, sh, "頭の上下", "°", -90, 90, d.xRot(), v ->
                edit(id, cur -> cur.withTransform(cur.x(), cur.y(), cur.z(), cur.yRot(), (float) v,
                        cur.headYRot(), cur.bodyYRot()))));
        return y + sh + lay.gap();
    }

    // ---- 状態(燃えている・光っている・子ども・ダメージ) ----

    private int sectionStates(int y) {
        NpcData d = NpcManager.get(id).orElse(null);
        if (d == null) return y;
        Entity live = NpcManager.entityOf(id);
        boolean living = live == null || live instanceof LivingEntity;
        boolean canBaby = live != null && EntityReflect.hasMethodBefore(live, "setBaby", Mob.class);

        List<String[]> flags = new ArrayList<>();
        flags.add(new String[] {EntityFlags.FIRE, "燃えている"});
        flags.add(new String[] {EntityFlags.GLOW, "光っている"});
        if (canBaby) flags.add(new String[] {EntityFlags.BABY, "子ども"});
        if (living) flags.add(new String[] {EntityFlags.DAMAGE, "ダメージ"});

        int cgap = Math.max(1, Theme.u(3));
        int cw = (w - cgap) / 2;
        for (int i = 0; i < flags.size(); i++) {
            String key = flags.get(i)[0];
            int cx = x + (i % 2) * (cw + cgap);
            int cy = y + (i / 2) * lay.sliderH();
            add(new ModernCheckbox(cx, cy, cw, lay.sliderH(), flags.get(i)[1], d.option(key, 0) == 1,
                    on -> edit(id, cur -> cur.withOption(key, on ? 1 : 0))));
        }
        int rows = (flags.size() + 1) / 2;
        return y + rows * lay.sliderH() + lay.gap();
    }

    // ---- 姿勢・モデル ----

    private int sectionPosture(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int pgap = Math.max(1, Theme.u(3));

        // 姿勢: モブごとに、意味のあるものだけを出す(選べるものがなければ、欄ごと出さない)
        List<NpcPose> poses = NpcData.PLAYER.equals(d.kind())
                ? NpcPose.PLAYER : EntityProfiles.of(d.entityType()).poses();
        if (poses.size() > 1) {
            label("姿勢", x, y, Theme.TEXT_DIM);
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
            label("モデル(腕の太さ)", x, y, Theme.TEXT_DIM);
            y += lh;
            String[] modelLabels = {"自動", "ノーマル", "スリム"};
            String[] modelValues = {null, "wide", "slim"};
            int mw = (w - pgap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                String value = modelValues[i];
                add(new ModernButton(x + i * (mw + pgap), y, mw, lay.btnH(), modelLabels[i],
                        ModernButton.Style.DEFAULT, () -> {
                    edit(id, cur -> cur.withModel(Optional.ofNullable(value)));
                    host.refresh();
                }).setSelected(java.util.Objects.equals(d.model().orElse(null), value)));
            }
            y += lay.btnH() + lay.gap();
        }
        return y;
    }

    // ---- アニメーション ----

    private int sectionAnimation(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        label("アニメーション", x, y, Theme.TEXT_DIM);
        y += lh;
        add(new ModernSlider(x, y, w, lay.sliderH(), "時間", "tick", 0,
                EntityTweaks.animTickMax(d.entityType()), 0.1, d.animTickOrDefault(),
                v -> edit(id, cur -> cur.withAnimTick((float) v))));
        return y + lay.sliderH() + lay.gap();
    }

    // ---- 動き(歩行・腕) ----

    private int sectionMotion(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();

        Entity live = NpcManager.entityOf(id);
        boolean living = live == null || live instanceof LivingEntity;   // エンドクリスタルやボートは、生き物ではない
        boolean isPlayer = NpcData.PLAYER.equals(d.kind());
        boolean showWalk = living && (isPlayer || EntityProfiles.of(d.entityType()).walks());
        boolean showArms = living && (isPlayer || EntityProfiles.of(d.entityType()).isHumanoid());

        if (showWalk || showArms) {
            label("動き", x, y, Theme.TEXT_DIM);
            y += lh;
        }
        if (showWalk) {
            add(new ModernSlider(x, y, w, sh, "歩行", "%", -100, 100, d.option(EntityMotion.WALK, 0),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.WALK, (int) v))));
            y += sh;
        }
        if (showArms) {
            int last = EntityMotion.ARM_NAMES.length - 1;
            ModernSlider arms = new ModernSlider(x, y, w, sh, "腕", "", 0, last, d.option(EntityMotion.ARMS, 0),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.ARMS, (int) v)));
            arms.setValueFormatter(v -> EntityMotion.ARM_NAMES[Math.max(0, Math.min(last, (int) Math.round(v)))]);
            add(arms);
            y += sh;
        }

        // [DEV] キーフレーム歩行の、周期と強さの調整
        if (CinemaState.devMode && showWalk) {
            EntityProfile walker = EntityProfiles.of(d.entityType());
            if (walker.walkCyclePosition() > 0f) {
                add(new ModernSlider(x, y, w, sh, "[DEV] 歩行周期", "秒", 0.2, 6.0, 0.05,
                        walker.walkCycleSeconds(), v -> {
                    walker.setWalkCycleSeconds((float) v);
                    edit(id, cur -> cur);   // 歩行を、新しい周期で反映し直す
                }));
                y += sh;
                add(new ModernSlider(x, y, w, sh, "[DEV] 歩行の強さ", "", 0.05, 1.0, 0.05,
                        walker.walkKeyframeIntensity(), v -> {
                    walker.setWalkKeyframeIntensity((float) v);
                    edit(id, cur -> cur);
                }));
                y += sh;
                add(new ModernButton(x, y, w, lay.btnH(), "周期をコピー", ModernButton.Style.DEFAULT, () -> {
                    String line = String.format(java.util.Locale.ROOT, "walkKeyframe(%.2ff, %.1ff, %.2ff);",
                            walker.walkCycleSeconds(), walker.walkMaxAnimationSpeed(), walker.walkKeyframeIntensity());
                    Minecraft.getInstance().keyboardHandler.setClipboard(line);
                    Cinema.LOGGER.info("[Cinema] {} -> {}", d.entityType(), line);
                }));
                y += lay.btnH() + lay.gap();
            }
        }

        if (showWalk || showArms) y += lay.gap();
        return y;
    }

    // ---- エンティティ固有のオプション(プロフィールが宣言したものだけ) ----

    private int sectionOptions(NpcData d, int y) {
        List<EntityOption> specs = EntityProfiles.of(d.entityType()).options();
        if (specs.isEmpty()) return y;
        int sh = lay.sliderH();

        for (EntityOption spec : specs) {
            if (spec.kind() == EntityOption.Kind.CHECKBOX) {
                add(new ModernCheckbox(x, y, w, sh, spec.label(), d.option(spec.key(), spec.def()) == 1,
                        on -> edit(id, cur -> cur.withOption(spec.key(), on ? 1 : 0))));
            } else if (spec.kind() == EntityOption.Kind.DROPDOWN) {
                add(new ModernDropdown(x, y, w, sh, spec.label(), spec.names(), d.option(spec.key(), spec.def()),
                        i -> edit(id, cur -> cur.withOption(spec.key(), i)), host::onDropdownOpen));
            } else {
                ModernSlider slider = new ModernSlider(x, y, w, sh, spec.label(), "",
                        spec.min(), spec.max(), d.option(spec.key(), spec.def()),
                        v -> edit(id, cur -> cur.withOption(spec.key(), (int) v)));
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

    // ---- 装備 ----

    private int sectionEquipment(NpcData d, int y) {
        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();

        // スロットは、クリックで外す。右クリックで装飾(エンチャント光沢・トリム)。入れるのは、インベントリの画面から
        label(Theme.fit(host.font(), "装備(クリックで外す・右クリックで装飾)", w), x, y, Theme.TEXT_DIM);
        y += lh;

        boolean animalGear = EntityProfiles.of(d.entityType()).hasAnimalGear();
        int cgap = Math.max(1, Theme.u(3));
        int cw = (w - cgap * 3) / 4;
        int ch = Math.max(Theme.u(26), 26);

        // 上段: インベントリ / メイン / オフ / 全削除
        add(new ItemButton(x, y, cw, ch, ItemStack.EMPTY, new ItemStack(Items.CHEST), b -> openInventory())
                .tip("インベントリを開く(アイテムを持ってきて、装備させる)"));
        addSlotButton(d, EquipmentSlot.MAINHAND, x + (cw + cgap), y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.OFFHAND, x + (cw + cgap) * 2, y, cw, ch, animalGear);
        add(new ItemButton(x + (cw + cgap) * 3, y, cw, ch, ItemStack.EMPTY, new ItemStack(Items.BARRIER), b -> {
            for (EquipmentSlot s : EquipmentEdit.SLOTS) EquipmentEdit.set(id, s, ItemStack.EMPTY);
            host.refresh();
        }).tip("装備をすべて外す"));
        y += ch + cgap;

        // 下段: 頭 / 胸 / 脚 / 足
        addSlotButton(d, EquipmentSlot.HEAD, x, y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.CHEST, x + (cw + cgap), y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.LEGS, x + (cw + cgap) * 2, y, cw, ch, animalGear);
        addSlotButton(d, EquipmentSlot.FEET, x + (cw + cgap) * 3, y, cw, ch, animalGear);
        y += ch + lay.gap();

        // 手の動作(使えるアイテムを持っているときだけ)
        ItemStack mainItem = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);
        ItemStack offItem = EquipmentEdit.stackOf(d, EquipmentSlot.OFFHAND);
        boolean anyMotion = EntityMotion.isSpear(mainItem) || hasUseMotion(mainItem) || hasUseMotion(offItem)
                || mainItem.is(Items.CROSSBOW) || offItem.is(Items.CROSSBOW);
        if (anyMotion) {
            label("手の動作", x, y, Theme.TEXT_DIM);
            y += lh;
        }

        if (EntityMotion.isSpear(mainItem)) {
            // 槍: 突き(ジャブ)と突進(チャージ)は、別の動作。どちらの進み具合も、同じスライダーで決める
            add(new ModernDropdown(x, y, w, sh, "槍の攻撃", List.of(EntityMotion.SPEAR_NAMES),
                    d.option(EntityMotion.SPEAR, 0),
                    i -> edit(id, cur -> cur.withOption(EntityMotion.SPEAR, i)), host::onDropdownOpen));
            y += sh;
            add(new ModernSlider(x, y, w, sh, "槍の進み具合", "%", 0, 100, d.option(EntityMotion.SPEAR_AMOUNT, 50),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.SPEAR_AMOUNT, (int) v))));
            y += sh;
        } else if (hasUseMotion(mainItem)) {
            add(new ModernSlider(x, y, w, sh, "メイン 使用モーション", "%", 0, 100,
                    d.option(EntityMotion.USE_MAIN, EntityMotion.defaultUse(mainItem)),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.USE_MAIN, (int) v))));
            y += sh;
        }
        if (hasUseMotion(offItem)) {
            add(new ModernSlider(x, y, w, sh, "オフ 使用モーション", "%", 0, 100,
                    d.option(EntityMotion.USE_OFF, EntityMotion.defaultUse(offItem)),
                    v -> edit(id, cur -> cur.withOption(EntityMotion.USE_OFF, (int) v))));
            y += sh;
        }
        if (mainItem.is(Items.CROSSBOW) || offItem.is(Items.CROSSBOW)) {
            add(new ModernCheckbox(x, y, w, sh, "装填済み(矢をつがえて構える)", d.option(EntityMotion.LOADED, 0) == 1,
                    on -> edit(id, cur -> cur.withOption(EntityMotion.LOADED, on ? 1 : 0))));
            y += sh;
        }

        statusY = y + Theme.u(2);   // メッセージの行
        return y + lay.th() + Theme.u(6);
    }

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
        ItemStack stack = EquipmentEdit.stackOf(d, slot);
        String slotName = EquipmentEdit.label(slot, animalGear);
        ItemButton button = new ItemButton(bx, by, bw, bh, stack, EquipmentEdit.ghostOf(slot), b -> {
            if (stack.isEmpty()) {
                say(slotName + "は空です(インベントリを開いて、アイテムを入れられます)");
                return;
            }
            if (b == 0) {   // 左クリック: 外す
                EquipmentEdit.set(id, slot, ItemStack.EMPTY);
                host.refresh();
            } else {        // 右クリック: 装飾
                Minecraft.getInstance().setScreenAndShow(new EquipmentMenuScreen(host.screen(), slotName,
                        () -> NpcManager.get(id).map(cur -> EquipmentEdit.stackOf(cur, slot)).orElse(ItemStack.EMPTY),
                        s -> EquipmentEdit.set(id, slot, s)));
            }
        });
        button.tip(stack.isEmpty() ? slotName : slotName + ": " + stack.getHoverName().getString());
        add(button);
    }

    private void say(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    // ---- [DEV] 動作の長さの調整(AnimationState を使うモブ) ----

    private int sectionDev(NpcData d, int y) {
        if (!CinemaState.devMode || !(EntityProfiles.of(d.entityType()) instanceof AnimationModeProfile moded)) return y;
        int modeIdx = d.option(moded.optionKey(), 0);
        if (!moded.hasField(modeIdx)) return y;

        add(new ModernSlider(x, y, w, lay.sliderH(), "[DEV] 動作の長さ", "秒", 0.0, 12.0, 0.05,
                moded.secondsOf(modeIdx), v -> {
            moded.setSeconds(modeIdx, (float) v);
            edit(id, cur -> cur);   // 新しい長さで反映し直す
        }));
        y += lay.sliderH();
        add(new ModernButton(x, y, w, lay.btnH(), "長さをコピー", ModernButton.Style.DEFAULT, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(moded.secondsLine());
            Cinema.LOGGER.info("[Cinema] {} seconds -> {}", d.entityType(), moded.secondsLine());
        }));
        return y + lay.btnH() + lay.gap();
    }

    // ================= タブ「ポーズ」 =================

    /** パーツごとのポーズ(翼・頭・腕など)を、これから足していく場所。いまは枠だけ */
    private int buildPose(int y) {
        label("パーツごとのポーズ", x, y, Theme.TEXT);
        y += lay.th() + Theme.u(5);
        label(Theme.fit(host.font(), "翼・頭・腕などの角度を、ここで調整できる", w), x, y, Theme.TEXT_DIM);
        y += lay.th() + Theme.u(3);
        label(Theme.fit(host.font(), "ようにします(準備中)", w), x, y, Theme.TEXT_DIM);
        return y + lay.th();
    }

    // ================= スクロール =================

    private void applyScroll() {
        for (Item it : items) {
            AbstractWidget widget = it.widget();
            widget.setY(it.baseY() - scroll);
            // 中身の領域に、完全に収まっているものだけを、表示して、操作できるようにする
            boolean inside = widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= contentBottom;
            widget.visible = inside;
            widget.active = inside;
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

    /** ホイール(スライダー・数値欄が使わなかったときだけ、画面から呼ばれる) */
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!contains(mx, my) || maxScroll <= 0) return false;
        setScroll(scroll - (int) Math.round(scrollY * Theme.u(24)));
        return true;
    }

    /** 右端のスクロールバー。つまみをドラッグできる */
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
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
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

    // ================= 描画 =================

    /** パネルの背景と、文字。ウィジェットの前に呼ぶ */
    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : fixedLabels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
        for (Label l : scrollLabels) {
            int ly = l.y() - scroll;
            if (ly >= contentTop && ly + lay.th() <= contentBottom) {
                Theme.text(g, host.font(), l.text(), l.x(), ly, l.color());
            }
        }
        if (tab == RightTab.MAIN && System.currentTimeMillis() < statusUntil) {
            int sy = statusY - scroll;
            if (sy >= contentTop && sy + lay.th() <= contentBottom) {
                Theme.text(g, host.font(), Theme.fit(host.font(), status, w), x, sy, 0xFFE5A04B);
            }
        }
    }
}