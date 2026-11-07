package net.kztmc.mc.cinema.ui.panel.world;

import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.right.ItemButton;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「選択した項目を描画する」: 装備の装飾画面(鍛冶型の格子)と同じ見た目。
 * アイコンのボタンを押すたびに、描画のオン/オフが切り替わる。オン(描画する)のボタンが、選択色になる。
 */
public class RenderTogglesScreen extends Screen {
    private static final int COLS = 6;

    private final Screen parent;

    public RenderTogglesScreen(Screen parent) {
        super(Component.literal("選択した項目を描画する"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private static ItemStack iconOf(RenderToggles.Item item) {
        return new ItemStack(switch (item) {
            case SCOREBOARD -> Items.OAK_SIGN;
            case ACTION_BAR -> Items.WRITABLE_BOOK;
            case TITLE -> Items.ENCHANTED_BOOK;
            case BOSS_BAR -> Items.DRAGON_HEAD;
            case CHAT -> Items.PAPER;
            case BLOCKS -> Items.GRASS_BLOCK;
            case ENTITIES -> Items.PIG_SPAWN_EGG;
            case PARTICLES -> Items.FIREWORK_STAR;
            case NAME_TAGS -> Items.NAME_TAG;
            case PLAYERS -> Items.PLAYER_HEAD;
            case BEACON_BEAMS -> Items.BEACON;
        });
    }

    private int panelX() {
        return (width - Theme.u(240)) / 2;
    }

    private int panelTop() {
        int rows = (RenderToggles.Item.values().length + COLS - 1) / COLS;
        int cell = Math.max(Theme.u(24), 24);
        int gap = Math.max(1, Theme.u(3));
        int btnH = Math.max(Theme.u(22), 22);
        int total = Theme.textH() * 2 + Theme.u(12) + rows * (cell + gap) + btnH + gap * 2;
        return Math.max(Theme.u(10), (height - total) / 2);
    }

    @Override
    protected void init() {
        int w = Theme.u(240);
        int x = panelX();
        int y = panelTop() + Theme.textH() * 2 + Theme.u(12);
        int btnH = Math.max(Theme.u(22), 22);
        int gap = Math.max(1, Theme.u(3));
        int cell = Math.max(Theme.u(24), 24);
        int cw = (w - gap * (COLS - 1)) / COLS;

        RenderToggles.Item[] items = RenderToggles.Item.values();
        for (int i = 0; i < items.length; i++) {
            RenderToggles.Item item = items[i];
            int cx = x + (i % COLS) * (cw + gap);
            int cy = y + (i / COLS) * (cell + gap);
            addRenderableWidget(new ItemButton(cx, cy, cw, cell, iconOf(item), ItemStack.EMPTY, b -> {
                item.set(!item.on());
                WorldSettings.save();
                rebuildWidgets();
            }).setSelected(item.on()).tip(item.label + (item.on() ? "(描画する)" : "(描画しない)")));
        }
        int rows = (items.length + COLS - 1) / COLS;
        y += rows * (cell + gap) + gap;

        int half = (w - gap) / 2;
        addRenderableWidget(new ModernButton(x, y, half, btnH, "すべてオン", ModernButton.Style.DEFAULT, () -> {
            RenderToggles.resetAll();
            WorldSettings.save();
            rebuildWidgets();
        }));
        addRenderableWidget(new ModernButton(x + half + gap, y, w - half - gap, btnH, "閉じる",
                ModernButton.Style.DEFAULT, this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x = panelX();
        int y = panelTop();
        Theme.text(g, font, title.getString(), x, y, Theme.TEXT);
        Theme.text(g, font, "押すと切り替え(選択色 = 描画する)", x, y + Theme.textH() + Theme.u(4), Theme.TEXT_DIM);
    }
}