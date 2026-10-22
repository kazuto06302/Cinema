package net.kztmc.mc.cinema.entity;

import java.util.Map;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * 生き物(LivingEntity)共通の動き。歩行の手足の振りと、腕を振る動作。
 * アニメーション時間とは独立で、NPCごとの options に整数で保存される。
 */
public final class EntityMotion {
    private EntityMotion() {}

    /**
     * 歩行(%、-100〜100)。0で直立。プラスとマイナスで、前に出る足が左右入れ替わる。絶対値が歩幅の大きさ。
     * 手足の角度は「強さ × cos(位相)」で決まるので、位相は0に固定して、符号つきの強さだけで表す。
     */
    public static final String WALK = "walk";

    /** 腕を振る動作の進み具合(%、0〜100) */
    public static final String SWING = "swing";

    /** 攻撃姿勢(0/1)。ゾンビが腕を上げる、スケルトンが弓を構える、など。Mob のみ */
    public static final String AGGRESSIVE = "aggressive";

    /**
     * NPCの状態に反映する。
     * partialTick は、描画に使う補間値(時間スライダーの小数部分)。
     * 描画側の歩行の位置は「位置 - 強さ × (1 - 補間値)」なので、補間値の分を足して、表示される位置を常に0にする。
     */
    public static void apply(Entity e, Map<String, Integer> options, float partialTick) {
        if (!(e instanceof LivingEntity living)) return;

        float walk = options.getOrDefault(WALK, 0) / 100f;
        float swing = options.getOrDefault(SWING, 0) / 100f;

        // 歩行
        WalkAnimationState state = living.walkAnimation;
        EntityReflect.setFloat(state, "speedOld", walk);
        EntityReflect.setFloat(state, "speed", walk);
        EntityReflect.setFloat(state, "position", walk * (1.0f - partialTick));

        applySwing(living, swing);
        if (e instanceof Mob mob) {
            mob.setAggressive(options.getOrDefault(AGGRESSIVE, 0) == 1);
        }
    }

    /**
     * 腕を振る動作。LivingEntity#swingState(SwingState)の次の状態を使う。
     *   currentSwing : 振りの情報。null だと、描画側が「振っていない」と扱う
     *   oldAnimation / animation : 進み具合(0〜1)。描画は2つの間を補間するので、同じ値にして補間値に左右されないようにする
     */
    private static void applySwing(LivingEntity living, float swing) {
        Object swingState = EntityReflect.getObject(living, "swingState");
        if (swingState == null) return;

        if (swing > 0f && living.getCurrentSwing() == null) {
            // 公式の swing() で、振りの情報(手・種類・長さ)を初期化する。クライアント側なのでパケットは送られない
            living.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        } else if (swing <= 0f && living.getCurrentSwing() != null) {
            // 0%に戻したら、「振っている」状態も解除する
            EntityReflect.setObject(swingState, "currentSwing", null);
        }

        EntityReflect.setFloat(swingState, "oldAnimation", swing);
        EntityReflect.setFloat(swingState, "animation", swing);
    }
}