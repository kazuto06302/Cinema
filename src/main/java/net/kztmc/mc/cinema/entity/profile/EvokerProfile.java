package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** エヴォーカー。詠唱中は、腕を上げて呪文を唱える姿になる(腕の動きは時間で進む)。 */
public final class EvokerProfile extends EntityProfile {
    public EvokerProfile() {
        super("minecraft:evoker");
        toggle("cast", "詠唱", false);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        boolean cast = options.getOrDefault("cast", 0) == 1;
        EntityReflect.invokeWithEnumArg(e, "setIsCastingSpell", cast ? "FANGS" : "NONE");
    }
}