package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.minecraft.world.entity.Entity;

/**
 * イルカ。常に泳いでいる状態で、尾を振る動き(時間スライダーで進む)が出る。
 * 描画側は「動いているか」を、移動量で判断しているので、少しだけ速度を与える
 * (NPCはティックされないので、実際には動かない)。水中の姿勢は、WaterProfile が常に水中にする。
 */
public final class DolphinProfile extends WaterProfile {
    public DolphinProfile() {
        super(true, false, "minecraft:dolphin");
    }

    @Override
    protected void applyExtra(Entity e, float time, Map<String, Integer> options) {
        e.setDeltaMovement(0.02, 0.0, 0.0);
    }
}