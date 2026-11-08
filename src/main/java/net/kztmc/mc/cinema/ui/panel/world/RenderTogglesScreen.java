package net.kztmc.mc.cinema.ui.panel.world;

import java.util.ArrayList;
import java.util.List;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.panel.right.ItemButton;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「選択した項目を描画する」: 鍛冶型のボタンと同じ見た目(ItemButton)の格子。4列×3行で、アイコンの下に文字を添える。
 * 押すたびに、描画のオン/オフが切り替わる(オンは高い音、オフは低い音)。
 */
public class RenderTogglesScreen extends Screen {
    private static final int COLS = 4;

    private record Caption(String text, int x, int y, int w, boolean on) {}

    private final Screen parent;
    private final List<Caption> captions = new ArrayList<>();

    public RenderTogglesScreen(Screen parent) {
        super(Component.literal("選択した項目を描画する"));
        this.parent = parent;
    }

    @Override
    public void added() {
        UiSounds.open();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
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

    /** アイコンの下に出す、短い名前(正式な名前は、ツールチップに出る) */
    private static String shortLabel(RenderToggles.Item item) {
        return switch (item) {
            case SCOREBOARD -> "スコアボード";
            case ACTION_BAR -> "アクションバー";
            case TITLE -> "タイトル";
            case BOSS_BAR -> "ボスバー";
            case CHAT -> "チャット";
            case BLOCKS -> "ブロック";
            case ENTITIES -> "エンティティ";
            case PARTICLES -> "パーティクル";
            case NAME_TAGS -> "ネームタグ";
            case PLAYERS -> "プレイヤー";
            case BEACON_BEAMS -> "ビーコン";
        };
    }

    private int panelW() {
        return Math.min(width - Theme.u(20), Theme.u(300));
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int cellH() {
        return Math.max(Theme.u(24), 24);
    }

    private int gap() {
        return Math.max(1, Theme.u(3));
    }

    private int captionH() {
        return Theme.textH() + Theme.u(4);
    }

    private int rowH() {
        return cellH() + captionH() + gap();
    }

    private int headH() {
        return Theme.textH() * 2 + Theme.u(12);
    }

    private int panelTop() {
        int rows = (RenderToggles.Item.values().length + COLS - 1) / COLS;
        int btnH = Math.max(Theme.u(22), 22);
        int total = headH() + rows * rowH() + btnH + gap();
        return Math.max(Theme.u(10), (height - total) / 2);
    }

    @Override
    protected void init() {
        captions.clear();
        int w = panelW();
        int x = panelX();
        int y = panelTop() + headH();
        int gap = gap();
        int cw = (w - gap * (COLS - 1)) / COLS;

        RenderToggles.Item[] items = RenderToggles.Item.values();
        for (int i = 0; i < items.length; i++) {
            RenderToggles.Item item = items[i];
            int cx = x + (i % COLS) * (cw + gap);
            int cy = y + (i / COLS) * rowH();
            addRenderableWidget(new ItemButton(cx, cy, cw, cellH(), iconOf(item), ItemStack.EMPTY, b -> {
                item.set(!item.on());
                UiSounds.click();  // オン=高い、オフ=低い
                WorldSettings.save();
                rebuildWidgets();
            }).setSelected(item.on()).tip(item.label + (item.on() ? "(描画する)" : "(描画しない)")));
            captions.add(new Caption(shortLabel(item), cx, cy + cellH() + Theme.u(2), cw, item.on()));
        }

        int rows = (items.length + COLS - 1) / COLS;
        y += rows * rowH();
        int btnH = Math.max(Theme.u(22), 22);
        int half = (w - gap) / 2;
        addRenderableWidget(new ModernButton(x, y, half, btnH, "すべてオン", ModernButton.Style.DEFAULT, () -> {
            RenderToggles.resetAll();
            UiSounds.success();
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
        Theme.text(g, font, "押すと切り替え(選択色・白い文字 = 描画する)", x, y + Theme.textH() + Theme.u(4), Theme.TEXT_DIM);

        for (Caption c : captions) {
            String text = Theme.fit(font, c.text(), c.w());
            int tx = c.x() + (c.w() - Theme.textWidth(font, text)) / 2;
            Theme.text(g, font, text, tx, c.y(), c.on() ? Theme.TEXT : Theme.TEXT_DIM);
        }
    }
}