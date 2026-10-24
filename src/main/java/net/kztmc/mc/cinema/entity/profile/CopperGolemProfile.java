package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** 銅ゴーレム。動作は、AnimationState を番号で選ぶ(対応はログに出る)。酸化の段階も選べる。 */
public final class CopperGolemProfile extends AutoAnimationProfile {
    private static final String[] WEATHER = {"UNAFFECTED", "EXPOSED", "WEATHERED", "OXIDIZED"};

    public CopperGolemProfile() {
        super("minecraft:copper_golem");
        choice("weather", "酸化", 0, "なし", "わずか", "かなり", "完全");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        super.applyAnim(e, time, options);
        int w = Math.max(0, Math.min(WEATHER.length - 1, options.getOrDefault("weather", 0)));
        EntityReflect.invokeWithEnumArg(e, "setWeatherState", WEATHER[w]);
    }
}