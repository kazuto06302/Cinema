package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ウィザーの頭(飛んでいく弾)。青い頭は「危険な頭」で、黒い頭より大きな威力を持つ。 */
public final class WitherSkullProfile extends EntityProfile {
    public WitherSkullProfile() {
        super("minecraft:wither_skull");
        toggle("blue", "青い頭", false);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setDangerous"), options.getOrDefault("blue", 0) == 1);
    }
}