package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * 銅ゴーレム。待機と、アイテムを取る・置くの4つの動作(AnimationState は、この5個だけ)。酸化の段階も選べる。
 * 動作の長さ(秒)は、devモードの画面で合わせる。0は、実際の速さのまま。
 */
public final class CopperGolemProfile extends AnimationModeProfile {
    private static final String[] WEATHER = {"UNAFFECTED", "EXPOSED", "WEATHERED", "OXIDIZED"};
    private static final String[] STATES = {"IDLE", "GETTING_ITEM", "GETTING_NO_ITEM", "DROPPING_ITEM", "DROPPING_NO_ITEM"};

    public CopperGolemProfile() {
        super("state", "動作",
                new String[] {"待機", "アイテムを取る", "取るものなし", "アイテムを置く", "置くものなし"},
                new String[] {"idleAnimationState", "interactionGetItemAnimationState",
                        "interactionGetNoItemAnimationState", "interactionDropItemAnimationState",
                        "interactionDropNoItemAnimationState"},
                new float[] {0f, 0f, 0f, 0f, 0f},
                "minecraft:copper_golem");
        choice("weather", "酸化", 0, "なし", "わずか", "かなり", "完全");
    }

    @Override
    protected void onMode(Entity e, int mode, float time) {
        // 動作の状態(手に持つものの表示などが、これで切り替わる)。定数名は推測
        EntityReflect.invokeWithEnumArg(e, "setState", STATES[mode]);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        super.applyAnim(e, time, options);
        int w = Math.max(0, Math.min(WEATHER.length - 1, options.getOrDefault("weather", 0)));
        EntityReflect.invokeWithEnumArg(e, "setWeatherState", WEATHER[w]);
    }
}