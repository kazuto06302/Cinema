package net.kztmc.mc.cinema.entity;

import java.util.List;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
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

    /** 手に持つものの使用モーションの進み具合(%)。0なら使っていない。メインとオフのどちらか1つだけが有効(メイン優先) */
    public static final String USE_MAIN = "use_main";
    public static final String USE_OFF = "use_off";

    /** クロスボウを装填済みにするか(0/1) */
    public static final String LOADED = "loaded";

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
        applyHandUse(living, options);
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
            // キーフレームの歩行は、強さが少しで飽和するので、スライダーは周期の位置として使う。振れ幅は、プロフィールの値
            float amount = Math.min(1f, Math.abs(walk));
            speed = amount > 0f ? EntityProfiles.of(living).walkKeyframeIntensity() : 0f;
            position = amount * cycle;
        } else {
            speed = walk * EntityProfiles.of(living).walkSpeedMax();
            position = EntityProfiles.of(living).walkPhase();
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

    /**
     * 手に持つものの使用モーション(弓を引く・トライデントを構える・盾を構える・食べる、など)。
     * NPCはティックされないので、使用中の状態(使っているアイテム・残り時間・フラグ)を直接書く。
     * 描画側は「使用時間 - 残り時間」で、引き絞りの進み具合を決める。
     */
    private static void applyHandUse(LivingEntity living, Map<String, Integer> options) {
        // クロスボウの装填(装填済みのものは、矢をつがえた姿になる)
        boolean loaded = options.getOrDefault(LOADED, 0) == 1;
        for (EquipmentSlot s : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ItemStack held = living.getItemBySlot(s);
            if (held.is(Items.CROSSBOW)) {
                if (loaded) held.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW).getCraftingRemainder()));
                else held.remove(DataComponents.CHARGED_PROJECTILES);
            }
        }

        int main = options.getOrDefault(USE_MAIN, 0);
        int off = options.getOrDefault(USE_OFF, 0);
        EquipmentSlot slot;
        InteractionHand hand;
        int percent;
        if (main > 0 && !living.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
            slot = EquipmentSlot.MAINHAND;
            hand = InteractionHand.MAIN_HAND;
            percent = main;
        } else if (off > 0 && !living.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            slot = EquipmentSlot.OFFHAND;
            hand = InteractionHand.OFF_HAND;
            percent = off;
        } else {
            if (living.isUsingItem()) {   // 使用を解除する
                EntityReflect.setObject(living, "useItem", ItemStack.EMPTY);
                EntityReflect.setInt(living, "useItemRemaining", 0);
                EntityReflect.invokeFirst(living, List.of("setLivingEntityFlag"), 1, false);
                EntityReflect.invokeFirst(living, List.of("setLivingEntityFlag"), 2, false);
            }
            return;
        }

        ItemStack stack = living.getItemBySlot(slot);
        int duration = stack.getUseDuration(living);
        int used = Math.round(fullUseTicks(stack, duration) * percent / 100f);
        EntityReflect.setObject(living, "useItem", stack);
        EntityReflect.setInt(living, "useItemRemaining", duration - used);
        EntityReflect.invokeFirst(living, List.of("setLivingEntityFlag"), 1, true);                          // 使用中
        EntityReflect.invokeFirst(living, List.of("setLivingEntityFlag"), 2, hand == InteractionHand.OFF_HAND);  // オフハンドか
    }

    /** 100%に当たる、使用の時間(tick) */
    private static int fullUseTicks(ItemStack stack, int duration) {
        if (stack.is(Items.BOW)) return 20;
        if (stack.is(Items.CROSSBOW)) return 25;
        if (stack.is(Items.TRIDENT)) return 10;
        return duration > 0 && duration < 400 ? duration : 20;   // 食べ物などは、本来の使用時間。盾などの長いものは、20
    }
}