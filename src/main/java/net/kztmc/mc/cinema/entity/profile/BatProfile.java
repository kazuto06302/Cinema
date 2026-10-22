package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** コウモリ。飛ぶ・休む(逆さ)の、2種類のキーフレームアニメーション。 */
public final class BatProfile extends AnimationModeProfile {
    public BatProfile() {
        super("state", "状態", new String[] {"飛ぶ", "休む"},
                new String[] {"flyAnimationState", "restAnimationState"}, "minecraft:bat");
    }

    @Override
    protected void onMode(Entity e, int mode, float time) {
        // 休む姿勢(逆さまの吊り下げ)は、フラグでも切り替わる
        EntityReflect.invokeFirst(e, List.of("setResting"), mode == 1);
    }
}