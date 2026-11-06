package net.kztmc.mc.cinema.ui.panel.world;

import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernCheckbox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 「選択した項目を描画する」: 項目ごとに、描画のオン/オフを切り替える */
public class RenderTogglesScreen extends Screen {
    private final Screen parent;

    public RenderTogglesScreen(Screen parent) {
        super(Component.literal("選択した項目を描画する"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        RenderToggles.Item[] items = RenderToggles.Item.values();
        int w = Math.min(this.width - 40, 260);
        int rowH = 22;
        int x = (this.width - w) / 2;
        int y = 40;

        for (RenderToggles.Item item : items) {
            addRenderableWidget(new ModernCheckbox(x, y, w, rowH, item.label, item.on(), on -> {
                item.set(on);
                WorldSettings.save();
            }));
            y += rowH;
        }
        y += 8;
        addRenderableWidget(new ModernButton(x, y, w / 2 - 2, 20, "すべてオン", ModernButton.Style.DEFAULT, () -> {
            RenderToggles.resetAll();
            WorldSettings.save();
            rebuildWidgets();
        }));
        addRenderableWidget(new ModernButton(x + w / 2 + 2, y, w / 2 - 2, 20, "戻る", ModernButton.Style.PRIMARY, this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        g.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }
}