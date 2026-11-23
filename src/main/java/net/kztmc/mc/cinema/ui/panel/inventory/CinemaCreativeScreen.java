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
 * アイテム一覧とホットバーから、NPC装備用のコピーだけを取得できる。
 */
public class CinemaCreativeScreen extends CreativeModeInventoryScreen {
    private static final int HOTBAR_SIZE = 9;

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

    /**
     * Cinema独自のクリエイティブ画面では、プレイヤーインベントリを変更しない。
     *
     * - QUICK_MOVE: アイテムの種類・場所に関係なく禁止
     * - ホットバー: PICKUP / CLONE は元のスタックを残してカーソルへコピー
     * - アイテム一覧: Minecraft本来の取得・複製処理を使用
     * - それ以外のプレイヤーインベントリ操作: 禁止
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type) {
        if (slot == null) return;

        // シフトクリックは、アイテム一覧・ホットバー・ゴミ箱を含めてすべて禁止
        if (type == ContainerInput.QUICK_MOVE) return;

        if (slot.container instanceof Inventory) {
            // ホットバーからは移動ではなく、アイテム一覧と同じようにコピーする
            if (slot.getSlotIndex() < HOTBAR_SIZE
                    && (type == ContainerInput.PICKUP || type == ContainerInput.CLONE)) {
                ItemStack source = slot.getItem();
                if (!source.isEmpty()) {
                    ItemStack copy = source.copy();
                    int count = type == ContainerInput.PICKUP && mouseButton == 1
                            ? 1
                            : copy.getMaxStackSize();
                    copy.setCount(Math.min(count, copy.getMaxStackSize()));
                    this.getMenu().setCarried(copy);
                }
            }

            // プレイヤーの実際のインベントリには一切書き込まない
            return;
        }

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
