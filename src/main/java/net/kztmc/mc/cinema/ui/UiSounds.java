package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** 画面の効果音。 */
public final class UiSounds {
    private UiSounds() {}

    /** タブの切り替え: ui.button.click を、高い音(pitch 2)で鳴らす */
    public static void tab() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 2.0F));
    }
}