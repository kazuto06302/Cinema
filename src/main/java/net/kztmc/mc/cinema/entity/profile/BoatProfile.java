package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ボート・イカダ(チェスト付きも)。IDは、EntityProfiles.of が名前の末尾(_boat / _raft)で判定して割り当てる。
 * オールは、常に漕いでいる状態で、時間スライダーに合わせて、位相が進む(1tickに π/8)。当たり判定は無効にする。
 */
public final class BoatProfile extends EntityProfile {
    public BoatProfile() {
        super();
        noHitbox();
        noWalk();
    }

    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        float phase = time * (float) Math.PI / 8f;
        EntityReflect.setFloat(state, "rowingTimeLeft", phase);
        EntityReflect.setFloat(state, "rowingTimeRight", phase);
    }
}