package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** フグ。膨らみ(0〜2)と、水中。 */
public final class PufferfishProfile extends WaterProfile {
    public PufferfishProfile() {
        super(true, false, "minecraft:pufferfish");
        dropdownChoice("puff", "膨らみ", 2, "しぼむ", "半分", "ふくらむ");
    }

    @Override
    protected void applyExtra(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setPuffState"), options.getOrDefault("puff", 2));
    }
}