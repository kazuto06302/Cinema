package net.kztmc.mc.cinema.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.npc.NpcPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 撮影用の編集画面。左: NPC一覧と追加 / 右: 選択中NPCの向き・ポーズ・装備。
 * 標準ウィジェットの上に半透明パネルを敷いた構成(独自ウィジェットは後で差し替え)。
 */
public class CinemaScreen extends Screen {
    private static final int PANEL_W = 176;
    private static final int PAD = 6;
    private static final int PANEL_BG = 0xD00E1116;
    private static final int ACCENT = 0xFF4C8DFF;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SLOT_LABELS = {"メイン", "オフ", "頭", "胴", "脚", "足"};

    private static String lastMobId = "minecraft:zombie";

    private EditBox mobIdBox;

    public CinemaScreen() {
        super(Component.literal("Cinema"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        buildLeftPanel();
        NpcManager.selectedData().ifPresent(this::buildRightPanel);
    }

    // ---- 左パネル ----

    private void buildLeftPanel() {
        int x = 8 + PAD;
        int w = PANEL_W - PAD * 2;
        int y = 8 + PAD;

        addRenderableWidget(new StringWidget(x, y, w, 10, Component.literal("NPC"), this.font));
        y += 16;

        addRenderableWidget(Button.builder(Component.literal("+ プレイヤー"), b -> {
            NpcManager.spawnAtPlayer(NpcData.PLAYER, "minecraft:player", "NPC");
            rebuildWidgets();
        }).bounds(x, y, w, 18).build());
        y += 22;

        mobIdBox = new EditBox(this.font, x, y, w, 18, Component.literal("mob"));
        mobIdBox.setMaxLength(100);
        mobIdBox.setValue(lastMobId);
        mobIdBox.setResponder(s -> lastMobId = s);
        addRenderableWidget(mobIdBox);
        y += 22;

        addRenderableWidget(Button.builder(Component.literal("+ モブ"), b -> {
            String id = lastMobId.contains(":") ? lastMobId : "minecraft:" + lastMobId;
            NpcManager.spawnAtPlayer(NpcData.MOB, id, null);
            rebuildWidgets();
        }).bounds(x, y, w, 18).build());
        y += 28;

        UUID sel = NpcManager.selectedId();
        for (NpcData d : NpcManager.defs()) {
            if (y > this.height - 34) break; // TODO: スクロール対応
            boolean isSel = d.id().equals(sel);
            String name = NpcData.PLAYER.equals(d.kind())
                    ? "[P] " + d.skin().orElse("NPC")
                    : "[M] " + d.entityType().replace("minecraft:", "");
            UUID id = d.id();
            addRenderableWidget(Button.builder(Component.literal((isSel ? "> " : "") + name), b -> {
                NpcManager.select(id);
                rebuildWidgets();
            }).bounds(x, y, w, 18).build());
            y += 20;
        }
    }

    // ---- 右パネル ----

    private void buildRightPanel(NpcData d) {
        UUID id = d.id();
        int x = this.width - PANEL_W - 8 + PAD;
        int w = PANEL_W - PAD * 2;
        int y = 8 + PAD;

        addRenderableWidget(new StringWidget(x, y, w, 10, Component.literal("選択中のNPC"), this.font));
        y += 16;

        float body = Mth.wrapDegrees(d.bodyYRot());
        float headOffset = Mth.clamp(Mth.wrapDegrees(d.headYRot() - d.bodyYRot()), -75f, 75f);

        addRenderableWidget(new ValueSlider(x, y, w, 18, "体の向き", -180, 180, body, v ->
                edit(id, cur -> {
                    float bo = (float) v;
                    float off = Mth.clamp(Mth.wrapDegrees(cur.headYRot() - cur.bodyYRot()), -75f, 75f);
                    float head = bo + off;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, bo);
                })));
        y += 20;

        addRenderableWidget(new ValueSlider(x, y, w, 18, "頭の左右", -75, 75, headOffset, v ->
                edit(id, cur -> {
                    float head = cur.bodyYRot() + (float) v;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, cur.bodyYRot());
                })));
        y += 20;

        addRenderableWidget(new ValueSlider(x, y, w, 18, "頭の上下", -90, 90, d.xRot(), v ->
                edit(id, cur -> cur.withTransform(cur.x(), cur.y(), cur.z(), cur.yRot(), (float) v,
                        cur.headYRot(), cur.bodyYRot()))));
        y += 24;

        addRenderableWidget(Button.builder(Component.literal("ポーズ: " + d.npcPose().label), b -> {
            NpcManager.get(id).ifPresent(cur -> NpcManager.replace(cur.withPose(cur.npcPose().next())));
            rebuildWidgets();
        }).bounds(x, y, w, 18).build());
        y += 22;

        addRenderableWidget(Button.builder(Component.literal("現在位置に移動"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float faceYaw = mc.player.getYRot() + 180f;
            NpcManager.get(id).ifPresent(cur -> NpcManager.replace(
                    cur.withTransform(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            faceYaw, cur.xRot(), faceYaw, faceYaw)));
            rebuildWidgets();
        }).bounds(x, y, w, 18).build());
        y += 28;

        addRenderableWidget(new StringWidget(x, y, w, 10, Component.literal("装備(手持ちを設定)"), this.font));
        y += 14;

        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack cur = d.equipment().get(slot.getName());
            String item = (cur == null || cur.isEmpty()) ? "-" : cur.getHoverName().getString();
            addRenderableWidget(Button.builder(Component.literal(SLOT_LABELS[i] + ": " + item), b -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                ItemStack held = mc.player.getMainHandItem().copy();
                setEquipment(id, slot, held);
                rebuildWidgets();
            }).bounds(x, y, w - 22, 18).build());
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                setEquipment(id, slot, ItemStack.EMPTY);
                rebuildWidgets();
            }).bounds(x + w - 20, y, 20, 18).build());
            y += 20;
        }

        y += 8;
        addRenderableWidget(Button.builder(Component.literal("このNPCを削除"), b -> {
            NpcManager.remove(id);
            rebuildWidgets();
        }).bounds(x, y, w, 18).build());
    }

    // ---- 編集ヘルパー ----

    private static void edit(UUID id, java.util.function.UnaryOperator<NpcData> fn) {
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

    // ---- 描画 ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // パネルを先に敷き、その上にウィジェットを描く
        panel(graphics, 8, 8, PANEL_W, this.height - 16);
        if (NpcManager.selectedId() != null) {
            panel(graphics, this.width - PANEL_W - 8, 8, PANEL_W, this.height - 16);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + 2, ACCENT); // 上端のアクセントライン
    }

    // ---- スライダー ----

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double cur,
                    DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(), (Mth.clamp(cur, min, max) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private double real() {
            return min + (max - min) * this.value;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return; // super コンストラクタ経由で呼ばれた場合の保険
            setMessage(Component.literal(label + ": " + Math.round(real()) + "°"));
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }
}
