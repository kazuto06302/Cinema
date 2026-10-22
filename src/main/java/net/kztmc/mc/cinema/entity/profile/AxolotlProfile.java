package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ウーパールーパー。姿勢は、BinaryAnimator(0〜1の切り替え。ticks / ticksOld が進み具合)の組み合わせで決まる。
 * ティックされないので、目標の値を直接書き込む。
 */
public final class AxolotlProfile extends EntityProfile {
    public AxolotlProfile() {
        super("minecraft:axolotl");
        choice("mode", "状態", 0, "水中", "地上", "死んだふり");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = options.getOrDefault("mode", 0);
        setFactor(e, "inWaterAnimator", mode == 0 || mode == 2 ? 1f : 0f);
        setFactor(e, "onGroundAnimator", mode == 1 ? 1f : 0f);
        setFactor(e, "playingDeadAnimator", mode == 2 ? 1f : 0f);
        setFactor(e, "movingAnimator", 0f);
    }

    private static void setFactor(Entity e, String field, float factor) {
        Object animator = EntityReflect.getObject(e, field);
        if (animator == null) return;
        int length = EntityReflect.getIntQuiet(animator, "animationLength", 10);
        int ticks = Math.round(factor * length);
        EntityReflect.setInt(animator, "ticks", ticks);
        EntityReflect.setInt(animator, "ticksOld", ticks);
    }
}