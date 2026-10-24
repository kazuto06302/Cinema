package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.profile.animation.NeutralMobs;
import net.minecraft.world.entity.Entity;

/** ハチ。攻撃モード(怒っている。赤い目の姿になる)。 */
public final class BeeProfile extends EntityProfile {
    public BeeProfile() {
        super("minecraft:bee");
        toggle("attack", "攻撃モード", false);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        NeutralMobs.setAngry(e, options.getOrDefault("attack", 0) == 1);
    }
}