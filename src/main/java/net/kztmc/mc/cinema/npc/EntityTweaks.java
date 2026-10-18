package net.kztmc.mc.cinema.npc;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.minecraft.world.entity.Entity;

/** 呼び出し元(NpcManager など)から見た窓口。実際の設定は entity/ パッケージの各 EntityProfile にある。 */
public final class EntityTweaks {
    private EntityTweaks() {}

    public static int defaultAnimTick(String entityTypeId) {
        return EntityProfiles.of(entityTypeId).defaultAnimTick();
    }

    public static int animTickMax(String entityTypeId) {
        return EntityProfiles.of(entityTypeId).animTickMax();
    }

    /** 生成直後に1回呼ぶ(アイコン用・NPC用の両方) */
    public static void prepare(Entity e) {
        EntityProfile profile = EntityProfiles.of(e);
        profile.prepare(e);
        applyAnim(e, profile.defaultAnimTick());
    }

    /** アニメーション時刻を反映する。NPCはティックされないので、ここで直接書き込む */
    public static void applyAnim(Entity e, int tick) {
        int t = Math.max(0, tick);
        e.tickCount = t;
        EntityProfiles.of(e).applyAnim(e, t);
    }
}