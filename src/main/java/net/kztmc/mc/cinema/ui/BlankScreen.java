package net.kztmc.mc.cinema.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 切り分け用: 背景を描かない、空の画面 */
public class BlankScreen extends Screen {
    public BlankScreen() {
        super(Component.literal("blank"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }
}