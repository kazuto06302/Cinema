package net.kztmc.mc.cinema.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.flag.FeatureFlagSet;

/** 標準のクリエイティブ画面(タブつき)に、NPCの装備パネルを重ねたもの。閉じると、Cinemaの画面に戻る。 */
public class CinemaCreativeScreen extends CreativeModeInventoryScreen {
    private final InventoryOverlay overlay = new InventoryOverlay(this, () -> this.getMenu().getCarried());

    public CinemaCreativeScreen(LocalPlayer player, FeatureFlagSet features, boolean operatorTab) {
        super(player, features, operatorTab);
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
}