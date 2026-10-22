package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.minecraft.world.entity.Entity;

/**
 * イルカ。「泳ぐ」にすると、尾を振る動き(時間で進む)が出る。描画側は「動いているか」を、移動量で判断しているので、
 * 少しだけ速度を与える(NPCはティックされないので、実際には動かない)。
 */
public final class DolphinProfile extends WaterProfile {
    public DolphinProfile() {
        super(true, "minecraft:dolphin");
        choice("swim", "泳ぎ", 1, "止まる", "泳ぐ");
    }

    @Override
    protected void applyExtra(Entity e, float time, Map<String, Integer> options) {
        boolean swim = options.getOrDefault("swim", 1) == 1;
        e.setDeltaMovement(swim ? 0.02 : 0.0, 0.0, 0.0);
    }
}