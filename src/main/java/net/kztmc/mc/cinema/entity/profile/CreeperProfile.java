package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * クリーパー。爆発前の膨らみは swell(0〜30)で決まる。点火中は1tickごとに1増える。
 * 描画側は swell / (maxSwell - 2) を膨らみ具合にして、大きくなりながら白く点滅する。
 * 帯電(雷に打たれた状態。青く光る膜が出る)は、チェックボックスで切り替える。
 */
public final class CreeperProfile extends EntityProfile {
    /** バニラの maxSwell。これに達すると爆発する */
    private static final int MAX_SWELL = 30;

    public CreeperProfile() {
        super("minecraft:creeper");
        toggle("charged", "帯電", false);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int n = (int) Math.floor(time);
        EntityReflect.setInt(e, "oldSwell", Math.min(n, MAX_SWELL));
        EntityReflect.setInt(e, "swell", Math.min(n + 1, MAX_SWELL));
        EntityReflect.setSyncedData(e, "DATA_IS_POWERED", options.getOrDefault("charged", 0) == 1);
    }
}