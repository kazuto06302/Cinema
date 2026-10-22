package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ファントム。翼の羽ばたきは時間で動く。サイズ(0〜20。バニラは 0〜64 まで)を選べる。 */
public final class PhantomProfile extends EntityProfile {
    public PhantomProfile() {
        super("minecraft:phantom");
        option("size", "サイズ", 0, 20, 0);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setPhantomSize"), options.getOrDefault("size", 0));
    }
}