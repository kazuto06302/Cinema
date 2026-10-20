package net.kztmc.mc.cinema.entity;

import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;

/**
 * 生き物(LivingEntity)共通の動き。歩行の手足の振りと、腕を振る動作。
 * アニメーション時間とは独立で、NPCごとの options に整数で保存される。
 */
public final class EntityMotion {
    private EntityMotion() {}

    /** 歩行の位相(度、0〜360)。0度と180度が最大の歩幅(左右が逆)、90度と270度は足がそろう */
    public static final String WALK_PHASE = "walk_phase";

    /** 歩行の強さ(%、0〜100)。手足の振れ幅 */
    public static final String WALK_AMOUNT = "walk_amount";

    /** 腕を振る動作の進み具合(%、0〜100) */
    public static final String SWING = "swing";

    /** 歩行の1周期の長さ。モデルは cos(位置 * 0.6662) で手足を振るので、2π / 0.6662 */
    private static final float WALK_CYCLE = (float) (2.0 * Math.PI / 0.6662);

    /**
     * NPCの状態に反映する。
     * partialTick は、描画に使う補間値(時間スライダーの小数部分)。
     * 描画側の歩行の位置は「位置 - 強さ × (1 - 補間値)」なので、補間値の分を足して、指定した位相がそのまま表示されるようにする。
     */
    public static void apply(Entity e, Map<String, Integer> options, float partialTick) {
        if (!(e instanceof LivingEntity living)) return;

        float phase = options.getOrDefault(WALK_PHASE, 0) / 360f * WALK_CYCLE;
        float amount = options.getOrDefault(WALK_AMOUNT, 0) / 100f;
        float swing = options.getOrDefault(SWING, 0) / 100f;

        // 歩行
        WalkAnimationState walk = living.walkAnimation;
        EntityReflect.setFloat(walk, "speedOld", amount);
        EntityReflect.setFloat(walk, "speed", amount);
        EntityReflect.setFloat(walk, "position", phase + amount * (1.0f - partialTick));

        // 腕を振る(old と同じ値にして、補間値に左右されないようにする)
        living.attackAnim = swing;
        living.oAttackAnim = swing;
    }
}