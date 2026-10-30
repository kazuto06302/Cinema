package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * エンドクリスタル。揺れと回転は、tickCount ではなく専用の time で決まる(描画は time + 補間値)。
 * バニラは time を乱数で初期化し、NPCはティックされないので、そのままだと時間スライダーの小数部分だけで動いて、
 * 1tickごとに元へ戻る。time を時刻に合わせて、動きを連続させる。
 */
public final class EndCrystalProfile extends EntityProfile {
    public EndCrystalProfile() {
        super("minecraft:end_crystal");
        toggle("bottom", "台座", true);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.setInt(e, "time", (int) Math.floor(time));
        EntityReflect.invokeFirst(e, List.of("setShowBottom"), options.getOrDefault("bottom", 1) == 1);
    }
}