package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * 銅ゴーレム。アイテムを取る・置くの動作と、酸化の段階。
 * フィールド名・メソッド名は推測。違っていたら、ログの警告と /cinema fields copper_golem で確認する。
 */
public final class CopperGolemProfile extends AnimationModeProfile {
    private static final String[] WEATHER = {"UNAFFECTED", "EXPOSED", "WEATHERED", "OXIDIZED"};

    public CopperGolemProfile() {
        super("state", "動作",
                new String[] {"待機", "アイテムを取る", "取るものなし", "アイテムを置く", "置くものなし"},
                new String[] {"idleAnimationState", "interactionGetItem", "interactionGetNoItem",
                        "interactionDropItem", "interactionDropNoItem"},
                "minecraft:copper_golem");
        choice("weather", "酸化", 0, "なし", "わずか", "かなり", "完全");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        super.applyAnim(e, time, options);
        int w = Math.max(0, Math.min(WEATHER.length - 1, options.getOrDefault("weather", 0)));
        EntityReflect.invokeWithEnumArg(e, "setWeatherState", WEATHER[w]);
    }
}