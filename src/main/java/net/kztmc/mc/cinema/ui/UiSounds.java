package net.kztmc.mc.cinema.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * 画面の効果音。音は、ここにまとめて定義して、部品からは、名前(click / tab / toggle ...)で呼ぶ。
 * 音を変えたいときは、ここだけを直す。pitch(高さ)で、意味を分ける。
 */
public final class UiSounds {
    private UiSounds() {}

    /** false にすると、画面の効果音を、すべて止める */
    public static boolean enabled = true;

    private static final float VANILLA_UI_VOLUME = 0.25f;
    /** 全体の音量の倍率 */
    private static final float MASTER_VOLUME = 1.0f;

    private static long lastDragSoundTime;

    // ---- 基本の再生(SoundEvent と Holder のどちらでも渡せる) ----

    public static void play(SoundEvent sound, float pitch, float volume) {
        if (!enabled) return;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, pitch, volume * MASTER_VOLUME * VANILLA_UI_VOLUME));
    }

    public static void play(Holder<SoundEvent> sound, float pitch, float volume) {
        if (!enabled) return;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound.value(), pitch, volume * MASTER_VOLUME * VANILLA_UI_VOLUME));
    }

    // ---- 名前つきの音 ----

    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public static void drag() {
        long now = System.currentTimeMillis();
        if (now - lastDragSoundTime < 60) return;
        lastDragSoundTime = now;
        play(SoundEvents.UI_BUTTON_CLICK, 2f, 0.4f);
    }

    public static void tab() {
        play(SoundEvents.UI_BUTTON_CLICK, 2.0f, 1.0f);
    }

    public static void open() {
        play(SoundEvents.UI_TOAST_IN, 1.2f, 0.7f);
    }

    public static void success() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.5f);
    }

    public static void deny() {
        play(SoundEvents.NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    public static void chime() {
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1f);
    }

    public static void equip() {
        play(SoundEvents.ARMOR_EQUIP_DIAMOND, 1.0f, 0.6f);
    }
}