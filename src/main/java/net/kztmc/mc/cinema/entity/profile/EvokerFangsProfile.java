package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** エヴォーカーの牙。lifeTicks が小さいほど牙が大きく開き、20tickで引っ込む。 */
public final class EvokerFangsProfile extends EntityProfile {
    /** 牙の lifeTicks の初期値(バニラ) */
    private static final int LIFE_START = 22;

    public EvokerFangsProfile() {
        super("minecraft:evoker_fangs");
        defaultAnim(9);   // 口が開いて見える位置
    }

    @Override
    public void prepare(Entity e) {
        e.setSilent(true);
        e.handleEntityEvent((byte) 4);
    }

    /** 描画側は 1 - (lifeTicks - 2 - 補間値) / 20 を使うので、lifeTicks = 22 - n で、進み具合が time/20 になる */
    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        EntityReflect.setInt(e, "lifeTicks", Math.max(1, LIFE_START - n));
    }
}