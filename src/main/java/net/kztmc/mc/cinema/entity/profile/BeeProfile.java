package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ハチ。攻撃モード(怒っている。赤い目の姿になる)。 */
public final class BeeProfile extends EntityProfile {
    public BeeProfile() {
        super("minecraft:bee");
        dropdownChoice("attack", "攻撃モード", 0, "なし", "あり");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        boolean attack = options.getOrDefault("attack", 0) == 1;
        EntityReflect.invokeFirst(e, List.of("setRemainingPersistentAngerTime"), attack ? 400 : 0);
    }
}