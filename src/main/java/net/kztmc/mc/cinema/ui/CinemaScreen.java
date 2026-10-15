package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.npc.NpcPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 撮影用の編集画面(モダンUI版)。
 *   左パネル: タブ[NPC一覧 / モブ追加]
 *   右パネル: 選択中NPCの向き・ポーズ・位置・装備
 * ゲーム画面は暗くしない。上部中央のボタンでUI全体を隠せる。
 */
public class CinemaScreen extends Screen {
    private static final int PANEL_W = 204;
    private static final int M = 8;     // 画面端の余白
    private static final int PAD = 8;   // パネル内の余白

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SLOT_LABELS = {"メイン", "オフ", "頭", "胴", "脚", "足"};

    private enum Tab { NPCS, MOBS }

    private record Label(String text, int x, int y, int color) {}

    // 画面を開き直しても保持する状態
    private static Tab tab = Tab.NPCS;
    private static String search = "";
    private static boolean collapsed;

    private final List<Label> labels = new ArrayList<>();
    private ModernList<NpcData> npcList;
    private ModernList<MobCatalog.Entry> mobList;
    private double npcScroll;
    private double mobScroll;
    private boolean pendingRefresh;

    private int leftX, rightX, panelY, panelH;
    private boolean hasRight;
    private int searchX, searchY, searchW;

    public CinemaScreen() {
        super(Component.literal("Cinema"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** ゲーム画面を暗くしない(撮影構図を見ながら編集するため) */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    /** ウィジェットのクリック処理中に作り直すと危ないので、次の描画の頭で実行する */
    private void refresh() {
        pendingRefresh = true;
    }

    @Override
    protected void init() {
        labels.clear();
        npcList = null;
        mobList = null;
        hasRight = false;

        panelY = M;
        panelH = this.height - M * 2;
        leftX = M;
        rightX = this.width - PANEL_W - M;

        addRenderableWidget(new ModernButton((this.width - 76) / 2, M, 76, 18,
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
    }

    // ================= 左パネル =================

    private void buildLeft() {
        int x = leftX + PAD;
        int w = PANEL_W - PAD * 2;
        int y = panelY + 10;
        int bottom = panelY + panelH - PAD;

        labels.add(new Label("CINEMA", x, y, Theme.ACCENT));
        y += 16;

        int half = (w - 4) / 2;
        addRenderableWidget(new ModernButton(x, y, half, 18, "NPC (" + NpcManager.defs().size() + ")",
                ModernButton.Style.GHOST, () -> {
            tab = Tab.NPCS;
            refresh();
        }).setSelected(tab == Tab.NPCS));
        addRenderableWidget(new ModernButton(x + half + 4, y, half, 18, "モブ追加",
                ModernButton.Style.GHOST, () -> {
            tab = Tab.MOBS;
            refresh();
        }).setSelected(tab == Tab.MOBS));
        y += 24;

        if (tab == Tab.NPCS) {
            int listH = bottom - 24 - y;
            npcList = new ModernList<>(x, y, w, listH, "NPCはまだいません",
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

            addRenderableWidget(new ModernButton(x, bottom - 18, w, 18, "+ プレイヤーを追加",
                    ModernButton.Style.PRIMARY, () -> {
                NpcManager.spawnAtPlayer(NpcData.PLAYER, "minecraft:player", "NPC");
                refresh();
            }));
        } else {
            searchX = x;
            searchY = y;
            searchW = w;
            EditBox box = new EditBox(this.font, x + 6, y + 5, w - 12, 10, Component.literal("検索"));
            box.setBordered(false);
            box.setMaxLength(60);
            box.setHint(Component.literal("モブを検索..."));
            box.setValue(search);
            addRenderableWidget(box);
            y += 24;

            mobList = new ModernList<>(x, y, w, bottom - y, "該当するモブがありません",
                    MobCatalog.Entry::name,
                    e -> e.id().startsWith("minecraft:") ? "" : e.id().substring(0, e.id().indexOf(':')),
                    e -> false,
                    e -> {
                        NpcManager.spawnAtPlayer(NpcData.MOB, e.id(), null);
                        tab = Tab.NPCS;
                        refresh();
                    });
            mobList.setItems(MobCatalog.filter(search));
            mobList.setScroll(mobScroll);
            box.setResponder(s -> {
                search = s;
                mobList.setItems(MobCatalog.filter(s));
                mobList.setScroll(0);
            });
            addRenderableWidget(mobList);
        }
    }

    // ================= 右パネル =================

    private void buildRight(NpcData d) {
        UUID id = d.id();
        int x = rightX + PAD;
        int w = PANEL_W - PAD * 2;
        int y = panelY + 10;
        int bottom = panelY + panelH - PAD;

        String name = NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType());
        labels.add(new Label(Theme.fit(this.font, name, w), x, y, Theme.TEXT));
        y += 16;

        // --- 向き ---
        labels.add(new Label("向き", x, y, Theme.TEXT_DIM));
        y += 11;

        float body = Mth.wrapDegrees(d.bodyYRot());
        float headOffset = Mth.clamp(Mth.wrapDegrees(d.headYRot() - d.bodyYRot()), -75f, 75f);

        addRenderableWidget(new ModernSlider(x, y, w, 22, "体の向き", "°", -180, 180, body, v ->
                edit(id, cur -> {
                    float bo = (float) v;
                    float off = Mth.clamp(Mth.wrapDegrees(cur.headYRot() - cur.bodyYRot()), -75f, 75f);
                    float head = bo + off;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, bo);
                })));
        y += 22;
        addRenderableWidget(new ModernSlider(x, y, w, 22, "頭の左右", "°", -75, 75, headOffset, v ->
                edit(id, cur -> {
                    float head = cur.bodyYRot() + (float) v;
                    return cur.withTransform(cur.x(), cur.y(), cur.z(), head, cur.xRot(), head, cur.bodyYRot());
                })));
        y += 22;
        addRenderableWidget(new ModernSlider(x, y, w, 22, "頭の上下", "°", -90, 90, d.xRot(), v ->
                edit(id, cur -> cur.withTransform(cur.x(), cur.y(), cur.z(), cur.yRot(), (float) v,
                        cur.headYRot(), cur.bodyYRot()))));
        y += 28;

        // --- ポーズ ---
        labels.add(new Label("ポーズ", x, y, Theme.TEXT_DIM));
        y += 11;
        NpcPose[] poses = NpcPose.values();
        int gap = 3;
        int pw = (w - gap * (poses.length - 1)) / poses.length;
        for (int i = 0; i < poses.length; i++) {
            NpcPose p = poses[i];
            addRenderableWidget(new ModernButton(x + i * (pw + gap), y, pw, 18, p.label,
                    ModernButton.Style.DEFAULT, () -> {
                edit(id, cur -> cur.withPose(p));
                refresh();
            }).setSelected(d.npcPose() == p));
        }
        y += 24;

        // --- 位置 ---
        addRenderableWidget(new ModernButton(x, y, w, 18, "自分の位置に移動して向かい合う",
                ModernButton.Style.DEFAULT, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float faceYaw = mc.player.getYRot() + 180f;
            edit(id, cur -> cur.withTransform(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    faceYaw, cur.xRot(), faceYaw, faceYaw));
            refresh();
        }));
        y += 24;

        // --- 装備 ---
        labels.add(new Label("装備(手に持っているアイテムを設定)", x, y, Theme.TEXT_DIM));
        y += 11;
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack cur = d.equipment().get(slot.getName());
            String item = (cur == null || cur.isEmpty()) ? "-" : cur.getHoverName().getString();
            addRenderableWidget(new ModernButton(x, y, w - 22, 18, SLOT_LABELS[i] + "  " + item,
                    ModernButton.Style.DEFAULT, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                setEquipment(id, slot, mc.player.getMainHandItem().copy());
                refresh();
            }));
            addRenderableWidget(new ModernButton(x + w - 18, y, 18, 18, "×",
                    ModernButton.Style.GHOST, () -> {
                setEquipment(id, slot, ItemStack.EMPTY);
                refresh();
            }));
            y += 20;
        }

        // --- 削除(下端固定) ---
        addRenderableWidget(new ModernButton(x, bottom - 18, w, 18, "このNPCを削除",
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
            if (mobList != null) mobScroll = mobList.getScroll();
            rebuildWidgets();
        }

        if (!collapsed) {
            Theme.panel(g, leftX, panelY, PANEL_W, panelH);
            if (hasRight) Theme.panel(g, rightX, panelY, PANEL_W, panelH);
            if (tab == Tab.MOBS) {
                // 検索ボックスの背景
                Theme.roundRect(g, searchX, searchY, searchW, 20, Theme.SURFACE);
            }
            for (Label l : labels) {
                Theme.text(g, this.font, l.text(), l.x(), l.y(), l.color());
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}