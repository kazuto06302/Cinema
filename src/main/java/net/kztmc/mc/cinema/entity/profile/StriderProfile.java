package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ストライダー。マグマの上にいるか(いないと、紫色になって震える)。 */
public final class StriderProfile extends EntityProfile {
    public StriderProfile() {
        super("minecraft:strider");
        toggle("lava", "マグマ", true);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setSuffocating"), options.getOrDefault("lava", 1) == 0);
    }
}