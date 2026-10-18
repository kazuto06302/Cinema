package net.kztmc.mc.cinema.entity.profile.animation;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 羽ばたき(ニワトリ・オウム共通)。
 * バニラの aiStep の「空中にいるとき」の更新式を、指定tick回だけ繰り返した結果を書き込む。
 * 描画側は、(sin(flap) + 1) * flapSpeed を羽の角度に使う。
 */
public final class FlapAnimation {
    public FlapAnimation() {}

    public static void apply(Entity e, int tick) {
        float flap = 0f, oFlap = 0f;
        float flapSpeed = 0f, oFlapSpeed = 0f;
        float flapping = 1.0f;

        for (int i = 0; i < Math.min(tick, 200); i++) {
            oFlap = flap;
            oFlapSpeed = flapSpeed;
            flapSpeed = Mth.clamp(flapSpeed + 4.0f * 0.3f, 0.0f, 1.0f);   // 空中: 羽がすぐ開く
            if (flapping < 1.0f) flapping = 1.0f;                       // 空中では毎tick 1.0 に戻る
            flapping *= 0.9f;
            flap += flapping * 2.0f;                                    // 1tickで約1.8rad進む
        }

        EntityReflect.setFloat(e, "flap", flap);
        EntityReflect.setFloat(e, "oFlap", oFlap);
        EntityReflect.setFloat(e, "flapSpeed", flapSpeed);
        EntityReflect.setFloat(e, "oFlapSpeed", oFlapSpeed);
    }
}