package net.kztmc.mc.cinema.entity.profile.animation;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 羽ばたき(ニワトリ・オウム共通)。
 * バニラの aiStep の「空中にいるとき」の更新式を、n回繰り返した状態を old、もう1回進めた状態を current として書き込む。
 * 描画側は、(sin(flap) + 1) * flapSpeed を羽の角度に使う。
 */
public final class FlapAnimation {
    private FlapAnimation() {}

    private static final class State {
        float flap;
        float flapSpeed;
        float flapping = 1.0f;

        void step() {
            flapSpeed = Mth.clamp(flapSpeed + 4.0f * 0.3f, 0.0f, 1.0f);   // 空中: 羽がすぐ開く
            if (flapping < 1.0f) flapping = 1.0f;                        // 空中では毎tick 1.0 に戻る
            flapping *= 0.9f;
            flap += flapping * 2.0f;                                     // 1tickで約1.8rad進む
        }
    }

    public static void apply(Entity e, float time) {
        int n = (int) Math.floor(time);
        State s = new State();
        for (int i = 0; i < Math.min(n, 400); i++) s.step();

        float oFlap = s.flap;
        float oFlapSpeed = s.flapSpeed;
        s.step();

        EntityReflect.setFloat(e, "oFlap", oFlap);
        EntityReflect.setFloat(e, "oFlapSpeed", oFlapSpeed);
        EntityReflect.setFloat(e, "flap", s.flap);
        EntityReflect.setFloat(e, "flapSpeed", s.flapSpeed);
    }
}