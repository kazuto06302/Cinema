package net.kztmc.mc.cinema.entity.profile.animation;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.AnimationState;

/**
 * キーフレームアニメーション(AnimationState)の操作。
 * 描画側の経過時間は「(ageInTicks - 開始tick) × 50ms」。NPCはティックされないので、
 * 開始tickを逆算して、「いま、動作の何tick目か」を指定する。
 */
public final class AnimationStates {
    private AnimationStates() {}

    /**
     * owner のフィールド field(AnimationState)を、再生中にする。
     * @param ageTicks     いまの時刻(tickCount + 補間値)。描画側が ageInTicks として使う値
     * @param elapsedTicks 動作の何tick目を表示するか。時間を伸縮するときは、ageTicks と違う値にする
     */
    public static void play(Object owner, String field, float ageTicks, float elapsedTicks) {
        if (!(EntityReflect.getObject(owner, field) instanceof AnimationState state)) return;
        // 開始tickは整数なので、誤差は1tick未満。伸縮しないとき(elapsed = age)は、開始tickが0で、誤差はない
        state.start((int) Math.floor(ageTicks - elapsedTicks));
    }

    /** 伸縮しない版(動作の時刻 = いまの時刻) */
    public static void play(Object owner, String field, float timeTicks) {
        play(owner, field, timeTicks, timeTicks);
    }

    public static void stop(Object owner, String field) {
        if (EntityReflect.getObject(owner, field) instanceof AnimationState state) {
            state.stop();
        }
    }
}