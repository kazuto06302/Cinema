package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ムーシュルーム。赤と茶色(雷に打たれると茶色になる)。 */
public final class MooshroomProfile extends EntityProfile {
    private static final String[] VARIANTS = {"RED", "BROWN"};

    public MooshroomProfile() {
        super("minecraft:mooshroom");
        choice("variant", "種類", 0, "赤", "茶(雷)");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int v = Math.max(0, Math.min(VARIANTS.length - 1, options.getOrDefault("variant", 0)));
        EntityReflect.invokeWithEnumArg(e, "setVariant", VARIANTS[v]);
    }
}