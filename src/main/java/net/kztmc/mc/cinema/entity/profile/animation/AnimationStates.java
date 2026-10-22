package net.kztmc.mc.cinema.entity.profile.animation;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.AnimationState;

/**
 * キーフレームアニメーション(AnimationState)の操作。
 * NPCはティックされないので、再生の開始は自分で行う。時刻は、描画側が「tickCount + 補間値」で求めるので、
 * 開始時刻を0にして、経過時間も時刻に合わせておけば、時間スライダーがそのままアニメーションの位置になる。
 */
public final class AnimationStates {
    private AnimationStates() {}

    /** owner のフィールド field(AnimationState)を、timeTicks の位置で再生中にする */
    public static void play(Object owner, String field, float timeTicks) {
        if (!(EntityReflect.getObject(owner, field) instanceof AnimationState state)) return;
        state.start(0);
        // 経過時間を溜めて進める版では、現在の時刻と同じ値にしておく(差分が0になり、位置が時刻から決まる)。版が違えば無視される
        long millis = Math.round(timeTicks * 50f);
        EntityReflect.setLongQuiet(state, "accumulatedTime", millis);
        EntityReflect.setLongQuiet(state, "lastTime", millis);
    }

    public static void stop(Object owner, String field) {
        if (EntityReflect.getObject(owner, field) instanceof AnimationState state) {
            state.stop();
        }
    }
}