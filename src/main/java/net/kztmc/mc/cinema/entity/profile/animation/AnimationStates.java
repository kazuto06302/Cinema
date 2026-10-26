package net.kztmc.mc.cinema.entity.profile.animation;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.AnimationState;

/**
 * キーフレームアニメーション(AnimationState)の操作。
 * 描画側の経過時間は「(ageInTicks - 開始tick) × 50ms」。開始tickは整数しか持てないので、
 * 開始tickは0に固定して、経過時間は、描画状態の ageInTicks を書き換えて決める
 * (AnimationModeProfile#adjustRenderState)。これで、時間を伸縮しても、誤差が出ない。
 */
public final class AnimationStates {
    private AnimationStates() {}

    /** owner のフィールド field(AnimationState)を、開始tick 0 で再生中にする */
    public static void play(Object owner, String field) {
        if (EntityReflect.getObject(owner, field) instanceof AnimationState state) {
            state.start(0);
        }
    }

    public static void stop(Object owner, String field) {
        if (EntityReflect.getObject(owner, field) instanceof AnimationState state) {
            state.stop();
        }
    }
}