package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** スノーゴーレム。かぼちゃの頭の有無。 */
public final class SnowGolemProfile extends EntityProfile {
    public SnowGolemProfile() {
        super("minecraft:snow_golem");
        choice("pumpkin", "かぼちゃ", 1, "なし", "あり");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setPumpkin"), options.getOrDefault("pumpkin", 1) == 1);
    }
}