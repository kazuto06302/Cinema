package net.kztmc.mc.cinema.ui;

import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/** ESC メニューに、Cinema の画面を開く、標準のボタンを足す */
public final class PauseMenuButton {
    private PauseMenuButton() {}

    private static final int BUTTON_H = 20;
    private static final int GAP = 4;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen) || client.level == null) return;
            List<AbstractWidget> buttons = Screens.getWidgets(screen);
            if (buttons.isEmpty()) return;   // 「ゲームを一時停止」だけの状態(ボタンなし)

            int minX = Integer.MAX_VALUE;
            int maxRight = Integer.MIN_VALUE;
            int maxBottom = Integer.MIN_VALUE;
            for (AbstractWidget b : buttons) {
                minX = Math.min(minX, b.getX());
                maxRight = Math.max(maxRight, b.getX() + b.getWidth());
                maxBottom = Math.max(maxBottom, b.getY() + b.getHeight());
            }
            int y = Math.min(maxBottom + GAP, scaledHeight - BUTTON_H - GAP);   // 画面の下に、はみ出さない

            buttons.add(Button.builder(Component.translatable("cinema.pause.open"),
                            button -> client.gui.setScreen(new CinemaScreen()))
                    .bounds(minX, y, maxRight - minX, BUTTON_H)
                    .build());
        });
    }
}