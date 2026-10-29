package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityMotion;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ブリーズ。アイドル・滑空・滑空から戻る・ロングジャンプ・発射・息を吸う。
 * 歩行スライダー: ブリーズは足で歩かず、風の渦と動きが ageInTicks で決まるので、歩行の値で位相(0〜40tick)を決める。
 */
public final class BreezeProfile extends AnimationModeProfile {
    /** 歩行100%が、動きの何tickぶんか(時間スライダーの全範囲と同じ) */
    private static final float CYCLE_TICKS = 40f;

    public BreezeProfile() {
        super("state", "動作",
                new String[] {"アイドル", "滑空", "滑空から戻る", "ロングジャンプ", "発射", "息を吸う"},
                new String[] {"idle", "slide", "slideBack", "longJump", "shoot", "inhale"},
                "minecraft:breeze");
    }

    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        float walk = options.getOrDefault(EntityMotion.WALK, 0) / 100f;
        if (walk != 0f) {
            EntityReflect.setFloat(state, "ageInTicks", Math.abs(walk) * CYCLE_TICKS);
        } else {
            super.adjustRenderState(e, state, time, options);
        }
    }
}