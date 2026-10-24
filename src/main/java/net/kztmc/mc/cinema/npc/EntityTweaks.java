package net.kztmc.mc.cinema.npc;

import java.util.Map;
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

    /** 生成直後に1回呼ぶ(アイコン用・NPC用の両方)。設定は無いものとして、初期値で反映する */
    public static void prepare(Entity e) {
        EntityProfile profile = EntityProfiles.of(e);
        profile.prepare(e);
        applyAnim(e, profile.defaultAnimTick(), Map.of());
    }

    public static void applyAnim(Entity e, float time) {
        applyAnim(e, time, Map.of());
    }

    /**
     * アニメーション時刻(小数)と、NPCごとの設定を反映する。NPCはティックされないので、ここで直接書き込む。
     * 描画側の時刻は「tickCount + 補間値」なので、tickCount = floor(time)、補間値 = 小数部分にする。
     * 順序: アニメーション(時刻から状態を書く) → Pose(その上に部位の調整を重ねる)
     */
    public static void applyAnim(Entity e, float time, Map<String, Integer> options) {
        float t = Math.max(0f, time);
        int n = (int) Math.floor(t);
        e.tickCount = n;
        SceneFreeze.setPartialTick(e, t - n);
        SceneFreeze.setOptions(e, options);

        EntityProfile profile = EntityProfiles.of(e);
        profile.applyAnim(e, t, options);
        profile.applyPose(e, t);
    }

    /** 描画状態ができた直後に呼ぶ(mixin とアイコン描画から)。時刻は tickCount + 補間値 */
    public static void adjustRenderState(Entity e, Object state, float partialTick) {
        EntityProfiles.of(e).adjustRenderState(e, state, e.tickCount + partialTick, SceneFreeze.optionsOf(e));
    }
}