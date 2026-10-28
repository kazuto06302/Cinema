package net.kztmc.mc.cinema.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;

/** 標準のインベントリ画面に、NPCの装備パネルを重ねたもの。閉じると、Cinemaの画面に戻る。 */
public class CinemaInventoryScreen extends InventoryScreen {
    private final InventoryOverlay overlay = new InventoryOverlay(this, () -> this.getMenu().getCarried());

    public CinemaInventoryScreen(Player player) {
        super(player);
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

    @Override
    public void onClose() {
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