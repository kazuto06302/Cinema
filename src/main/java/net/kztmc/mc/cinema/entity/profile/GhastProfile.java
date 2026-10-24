package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityMotion;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ガストとハッピーガスト。触手は ageInTicks で動くので、描画状態の ageInTicks を歩行スライダーの値で置き換える。
 * ガストには、攻撃モード(口を開けて火球を溜める)もある。
 */
public final class GhastProfile extends EntityProfile {
    /** 触手が1周する時間(tick)。触手は sin(ageInTicks * 0.3) で動くので、2π / 0.3 */
    private static final float CYCLE_TICKS = 21f;

    private final boolean hasAttack;

    public GhastProfile(String id, boolean hasAttack) {
        super(id);
        this.hasAttack = hasAttack;
        if (hasAttack) {
            dropdownChoice("attack", "攻撃モード", 0, "なし", "あり");
        }
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        if (hasAttack) {
            EntityReflect.invokeFirst(e, List.of("setCharging"), options.getOrDefault("attack", 0) == 1);
        }
    }

    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        float walk = options.getOrDefault(EntityMotion.WALK, 0) / 100f;
        EntityReflect.setFloat(state, "ageInTicks", walk * CYCLE_TICKS);
    }
}