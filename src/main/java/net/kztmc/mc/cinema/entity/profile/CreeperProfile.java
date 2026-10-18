package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * クリーパー。爆発前の膨らみは swell(0〜30)で決まる。点火中は1tickごとに1増える。
 * 描画側は swell / (maxSwell - 2) を膨らみ具合にして、大きくなりながら白く点滅する。
 */
public final class CreeperProfile extends EntityProfile {
    /** バニラの maxSwell。これに達すると爆発する */
    private static final int MAX_SWELL = 30;

    public CreeperProfile() {
        super("minecraft:creeper");
        defaultAnim(0);   // 膨らんで点滅している位置
    }

    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        EntityReflect.setInt(e, "oldSwell", Math.min(n, MAX_SWELL));
        EntityReflect.setInt(e, "swell", Math.min(n + 1, MAX_SWELL));
    }
}