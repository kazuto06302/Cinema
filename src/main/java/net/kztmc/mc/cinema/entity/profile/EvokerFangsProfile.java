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
        defaultAnim(14);   // 口が開いて見える位置
    }

    @Override
    public void prepare(Entity e) {
        e.setSilent(true);                // 「攻撃開始」の効果音を鳴らさない
        e.handleEntityEvent((byte) 4);    // これを呼ばないと牙は何も描画されない
    }

    @Override
    public void applyAnim(Entity e, int tick) {
        EntityReflect.setInt(e, "lifeTicks", Math.max(1, LIFE_START - tick));
    }
}