package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** カメ。水中かどうか(泳ぐ/歩く)と、卵を抱えているか。 */
public final class TurtleProfile extends WaterProfile {
    public TurtleProfile() {
        super(false, true, "minecraft:turtle");
        toggle("egg", "卵", false);
    }

    @Override
    protected void applyExtra(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setHasEgg"), options.getOrDefault("egg", 0) == 1);
    }
}