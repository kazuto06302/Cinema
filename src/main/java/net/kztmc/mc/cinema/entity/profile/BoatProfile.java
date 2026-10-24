package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ボート・イカダ(チェスト付きも)。IDは、EntityProfiles.of が名前の末尾(_boat / _raft)で判定して割り当てる。
 * オールの位相(rowingTimeLeft / Right)を、時間スライダーに合わせて動かす。当たり判定は無効にする。
 */
public final class BoatProfile extends EntityProfile {
    public BoatProfile() {
        super();
        choice("paddle", "オール", 1, "止める", "漕ぐ");
        noHitbox();
        noWalk();
    }

    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        boolean row = options.getOrDefault("paddle", 1) == 1;
        float phase = row ? time * (float) Math.PI / 8f : 0f;
        EntityReflect.setFloat(state, "rowingTimeLeft", phase);
        EntityReflect.setFloat(state, "rowingTimeRight", phase);
    }
}