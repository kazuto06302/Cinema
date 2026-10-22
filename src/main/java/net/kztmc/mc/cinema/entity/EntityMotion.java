package net.kztmc.mc.cinema.entity;

import java.util.Map;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * 生き物(LivingEntity)共通の動き。歩行と、腕のポーズ。
 * アニメーション時間とは独立で、NPCごとの options に整数で保存される。
 * ここは「状態を書く層」。将来のPose(部位ごとの角度の調整)は、この上に重ねる。
 */
public final class EntityMotion {
    private EntityMotion() {}

    /**
     * 歩行(%、-100〜100)。0で直立。
     *  - 手足が cos で振れるモデル: プラスとマイナスで前に出る足が入れ替わる。絶対値が歩幅。
     *  - キーフレームで歩くモデル(EntityProfile#walkKeyframe): 絶対値が、歩行の周期のどこにいるか。
     */
    public static final String WALK = "walk";

    /** 腕のポーズ。ヒト型のモデルだけで使う。番号は ARM_NAMES の並び */
    public static final String ARMS = "arms";

    public static final String[] ARM_NAMES = {"通常", "構える", "振りかぶる", "振っている", "振り終わり"};

    /** 腕のポーズごとの、振りの進み具合(0〜1)。0は振らない */
    private static final float[] SWING_OF_ARMS = {0f, 0f, 0.25f, 0.5f, 0.8f};

    /**
     * NPCの状態に反映する。
     * partialTick は、描画に使う補間値(時間スライダーの小数部分)。
     */
    public static void apply(Entity e, Map<String, Integer> options, float partialTick) {
        if (!(e instanceof LivingEntity living)) return;

        applyWalk(living, options.getOrDefault(WALK, 0) / 100f, partialTick);

        int arms = Math.max(0, Math.min(ARM_NAMES.length - 1, options.getOrDefault(ARMS, 0)));
        if (living instanceof Mob mob) {
            mob.setAggressive(arms == 1);   // 構える: ゾンビは腕を上げ、スケルトンは弓を構える
        }
        applySwing(living, SWING_OF_ARMS[arms]);
    }

    /**
     * 描画側の歩行の位置は「位置 - 強さ × (1 - 補間値)」なので、補間値の分を足して、表示される位置が指定どおりになるようにする。
     */
    private static void applyWalk(LivingEntity living, float walk, float partialTick) {
        WalkAnimationState state = living.walkAnimation;
        float cycle = EntityProfiles.of(living).walkCyclePosition();

        float speed;
        float position;
        if (cycle > 0f) {
            // キーフレームの歩行は、強さが少しで飽和するので、スライダーは周期の位置として使う
            float amount = Math.min(1f, Math.abs(walk));
            speed = amount > 0f ? 1f : 0f;
            position = amount * cycle;
        } else {
            speed = walk;
            position = 0f;
        }

        EntityReflect.setFloat(state, "speedOld", speed);
        EntityReflect.setFloat(state, "speed", speed);
        EntityReflect.setFloat(state, "position", position + speed * (1.0f - partialTick));
    }

    /**
     * 腕を振る動作。LivingEntity#swingState(SwingState)の次の状態を使う。
     *   currentSwing : 振りの情報。null だと、描画側が「振っていない」と扱う
     *   oldAnimation / animation : 進み具合(0〜1)。同じ値にして、補間値に左右されないようにする
     */
    private static void applySwing(LivingEntity living, float swing) {
        Object swingState = EntityReflect.getObject(living, "swingState");
        if (swingState == null) return;

        if (swing > 0f && living.getCurrentSwing() == null) {
            living.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        } else if (swing <= 0f && living.getCurrentSwing() != null) {
            EntityReflect.setObject(swingState, "currentSwing", null);
        }

        EntityReflect.setFloat(swingState, "oldAnimation", swing);
        EntityReflect.setFloat(swingState, "animation", swing);
    }
}