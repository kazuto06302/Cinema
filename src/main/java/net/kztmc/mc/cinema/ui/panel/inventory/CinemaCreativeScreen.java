package net.kztmc.mc.cinema.ui.panel.inventory;

import net.kztmc.mc.cinema.ui.CinemaScreen;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * クリエイティブ画面に、NPCの装備パネルを重ねたもの。
 * ゲームモード・OP に関係なく開く。実際の持ち物は変えず、サーバーへも何も送らない。
 * 使えるのは、タブのアイテム一覧から「取る」ことだけ(取ったものは、NPCの装備へ渡す)。
 */
public class CinemaCreativeScreen extends CreativeModeInventoryScreen {
    private final InventoryOverlay overlay = new InventoryOverlay(this, () -> this.getMenu().getCarried());

    public CinemaCreativeScreen(LocalPlayer player, FeatureFlagSet features, boolean operatorTab) {
        super(player, features, true);
    }

    @Override
    protected void init() {
        super.init();
        overlay.init(this.leftPos, this.topPos, this::addRenderableWidget);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (overlay.consumeRebuild()) rebuildWidgets();
        overlay.extractBefore(g, this.font);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** アイテム一覧から取る操作だけ通す。持ち物・ホットバー・画面外(ドロップ)・削除は、すべて止める */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type) {
        // インベントリタブのゴミ箱スロットに対するシフトクリック
        boolean trashQuickMove =
                this.isInventoryOpen() && slot != null
                        && type == ContainerInput.QUICK_MOVE
                        && !this.getMenu().slots.isEmpty()
                        && slot == this.getMenu().slots.get(
                        this.getMenu().slots.size() - 1
                );

        // ゴミ箱のシフトクリックだけMinecraft本来の処理に通す
        if (trashQuickMove) {
            super.slotClicked(slot, slotId, mouseButton, type);
            return;
        }
        // プレイヤーの実際のインベントリ操作は禁止
        if (slot == null || slot.container instanceof Inventory) return;
        // アイテム一覧からの取得・複製だけ許可
        if (type != ContainerInput.PICKUP && type != ContainerInput.CLONE) return;

        super.slotClicked(slot, slotId, mouseButton, type);
    }

    /** 数字キーによるホットバーへの入れ替えを止める */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.minecraft != null) {
            for (var key : this.minecraft.options.keyHotbarSlots) {
                if (key.matches(event)) return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.getMenu().setCarried(ItemStack.EMPTY);   // 持っているアイテムは、捨てずに消す(クライアント側だけ)
        UiSounds.drop();
        super.onClose();
        CinemaScreen.requestOpen();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overlay.handleClick(event, doubleClick)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (overlay.handleRelease(event)) return true;
        return super.mouseReleased(event);
    }
}