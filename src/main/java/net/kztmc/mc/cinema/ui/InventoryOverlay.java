package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.kztmc.mc.cinema.npc.EquipmentRules;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class InventoryOverlay {

    private static final EquipmentSlot[] ORDER = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final EquipmentSlot[] MAINHAND_ONLY = {
            EquipmentSlot.MAINHAND
    };

    private static final int COLS = 3;

    /** ドラッグして離したときに、スロットを当てるための領域 */
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w
                    && my >= y && my < y + h;
        }
    }

    private final Screen host;
    private final Supplier<ItemStack> carried;

    private final List<AbstractWidget> own = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    private int x, y, w, h, pad, statusY;

    private boolean hasNpc;
    private boolean pressInside;

    private String title = "";
    private String status = "";

    private long statusUntil;
    private boolean rebuild;

    public InventoryOverlay(Screen host, Supplier<ItemStack> carried) {
        this.host = host;
        this.carried = carried;
    }

    public boolean consumeRebuild() {
        boolean r = rebuild;
        rebuild = false;
        return r;
    }

    /**
     * @param inventoryLeft インベントリ本体の左端のx
     * @param top           インベントリ本体の上端のy
     */
    public void init(int inventoryLeft, int top, Consumer<AbstractWidget> add) {
        own.clear();
        hits.clear();
        Consumer<AbstractWidget> register = widget -> {
            own.add(widget);
            add.accept(widget);
        };

        pad = Math.max(3, Theme.u(6));
        int gap = Math.max(1, Theme.u(3));
        int th = Theme.textH();
        int btn = Math.max(Theme.u(26), 26);
        int btnH = Math.max(th + 3, Theme.u(14));

        w = btn * COLS + gap * (COLS - 1) + pad * 2;
        x = Math.max(Theme.u(6), inventoryLeft - w - Theme.u(8));
        y = top;
        Optional<NpcData> selected = NpcManager.selectedData();
        hasNpc = selected.isPresent();
        if (!hasNpc) {
            h = pad * 2 + th * 2;
            return;
        }

        NpcData d = selected.get();
        UUID id = d.id();
        // エンティティのプロフィールを取得
        EntityProfile profile = EntityProfiles.of(d.entityType());

        // 動物用装備かどうか
        boolean animalGear = profile.hasAnimalGear();

        // タイトル
        title = NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType());
        EquipmentSlot[] slots = profile.heldItemLabel() != null ? MAINHAND_ONLY : ORDER;

        int cy = y + pad + th + Theme.u(5);
        for (int i = 0; i < slots.length; i++) {
            EquipmentSlot slot = slots[i];
            int bx = x + pad + (i % COLS) * (btn + gap);
            int by = cy + (i / COLS) * (btn + gap);
            ItemStack stack = EquipmentEdit.stackOf(d, slot);
            String label = profile.heldItemLabel() != null ? profile.heldItemLabel() : EquipmentEdit.label(slot, animalGear);
            ItemButton button = new ItemButton(bx, by, btn, btn, stack, EquipmentEdit.ghostOf(slot), b -> onSlot(id, slot, animalGear, b, profile));
            button.tip(stack.isEmpty() ? label : label + ": " + stack.getHoverName().getString());
            register.accept(button);
            hits.add(new Hit(bx, by, btn, btn, () -> onSlot(id, slot, animalGear, 0, profile)));
        }

        int rows = (slots.length + COLS - 1) / COLS;
        int rowsBottom = cy + rows * (btn + gap);
        int bw = w - pad * 2;
        register.accept(new ModernButton(x + pad, rowsBottom, bw, btnH, "装備をすべて外す", ModernButton.Style.DEFAULT,
                () -> {
                    for (EquipmentSlot s : EquipmentEdit.SLOTS) {
                        EquipmentEdit.set(id, s, ItemStack.EMPTY);
                    }
                    rebuild = true;
                }
        ));
        register.accept(new ModernButton(x + pad, rowsBottom + btnH + gap, bw, btnH, "Cinemaに戻る", ModernButton.Style.PRIMARY, host::onClose));
        statusY = rowsBottom + (btnH + gap) * 2 + gap;
        h = statusY - y + (th + 2) * 4 + pad;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean handleClick(MouseButtonEvent event, boolean doubleClick) {
        if (!contains(event.x(), event.y())) {
            pressInside = false;
            return false;
        }
        pressInside = true;
        for (AbstractWidget widget : own) {
            if (widget.mouseClicked(event, doubleClick)) break;
        }
        return true;
    }

    public boolean handleRelease(MouseButtonEvent event) {
        boolean inside = contains(event.x(), event.y());
        if (inside && !pressInside && Theme.isClick(event.button())) {
            for (Hit hit : hits) {
                if (hit.contains(event.x(), event.y())) {
                    hit.action().run();
                    break;
                }
            }
        }
        pressInside = false;
        return inside;
    }

    // =========================================================
    // スロット操作
    // =========================================================

    private void onSlot(UUID id, EquipmentSlot slot, boolean animalGear, int button, EntityProfile profile) {
        String label = profile.heldItemLabel() != null ? profile.heldItemLabel() : EquipmentEdit.label(slot, animalGear);
        ItemStack current = NpcManager.get(id)
                .map(cur -> EquipmentEdit.stackOf(cur, slot))
                .orElse(ItemStack.EMPTY);
        // ---- 右クリック: 装飾 ----
        if (button == 1) {
            if (current.isEmpty()) {
                say(label + "は空です");
                return;
            }
            Minecraft.getInstance().setScreenAndShow(
                    new EquipmentMenuScreen(host, label,
                            () -> NpcManager.get(id)
                                    .map(cur -> EquipmentEdit.stackOf(cur, slot))
                                    .orElse(ItemStack.EMPTY),
                            s -> EquipmentEdit.set(id, slot, s)));
            return;
        }

        ItemStack in = carried.get();
        if (!in.isEmpty()) {
            // アイテムをつかんでいれば、入れる
            // EntityProfile によるアイテム制限も適用する
            if (!EquipmentRules.accepts(slot, in, animalGear, profile)) {
                say(label + "には入れられません: " + in.getHoverName().getString());
                return;
            }
            EquipmentEdit.set(id, slot, in.copy());
            rebuild = true;
        } else if (!current.isEmpty()) {
            EquipmentEdit.set(id, slot, ItemStack.EMPTY);
            rebuild = true;
        } else {
            say("アイテムをつかんでから、スロットをクリックしてください");
        }
    }

    private void say(String text) {
        status = text;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    public void extractBefore(GuiGraphicsExtractor g, Font font) {
        Theme.panel(g, x, y, w, h);
        if (!hasNpc) {
            Theme.text(g, font, "NPCを選んでください", x + pad, y + pad, Theme.TEXT_DIM);
            return;
        }
        Theme.text(g, font, Theme.fit(font, "装備先: " + title, w - pad * 2), x + pad, y + pad, Theme.TEXT);

        boolean showStatus = System.currentTimeMillis() < statusUntil;
        String text = showStatus ? status : "つかんだアイテムを、スロットへクリック(またはドラッグ)。クリックで外す・右クリックで装飾";
        int color = showStatus ? 0xFFE5A04B : Theme.TEXT_DIM;
        int ly = statusY;
        for (String line : wrap(font, text, w - pad * 2)) {
            Theme.text(g, font, line, x + pad, ly, color);
            ly += Theme.textH() + 2;
        }
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            line.append(text.charAt(i));
            if (Theme.textWidth(font, line.toString()) > maxWidth && line.length() > 1) {
                char last = line.charAt(line.length() - 1);
                line.setLength(line.length() - 1);
                lines.add(line.toString());
                line = new StringBuilder().append(last);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}