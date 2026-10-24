package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** シロクマ。四つ足・立ち上がる(時間0〜6で進む)・立っている。立ち上がりの進み具合は 0〜6。 */
public final class PolarBearProfile extends EntityProfile {
    private static final int STAND_TICKS = 6;

    public PolarBearProfile() {
        super("minecraft:polar_bear");
        choice("state", "姿勢", 0, "四つ足", "立ち上がる", "立っている");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = options.getOrDefault("state", 0);
        int n = (int) Math.floor(time);
        float old = mode == 0 ? 0f : mode == 1 ? Math.min(n, STAND_TICKS) : STAND_TICKS;
        float cur = mode == 0 ? 0f : mode == 1 ? Math.min(n + 1, STAND_TICKS) : STAND_TICKS;
        EntityReflect.setFloat(e, "clientSideStandAnimationO", old);
        EntityReflect.setFloat(e, "clientSideStandAnimation", cur);
        EntityReflect.invokeFirst(e, List.of("setStanding"), mode != 0);
    }
}