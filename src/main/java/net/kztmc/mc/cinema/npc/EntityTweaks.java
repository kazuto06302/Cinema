package net.kztmc.mc.cinema.npc;

import java.util.Map;

import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/** 呼び出し元(NpcManager など)から見た窓口。実際の設定は entity/ パッケージの各 EntityProfile にある。 */
public final class EntityTweaks {
    private EntityTweaks() {}
    private static boolean tridentLogged;

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

    /** 当たり判定を無効にするNPCか(ボート・イカダ・プレイヤー)。mixin から呼ばれる */
    public static boolean hasNoHitbox(Entity e) {
        return SceneFreeze.isFrozenNpc(e) && (e instanceof NpcPlayer || EntityProfiles.of(e).hasNoHitbox());
    }

    private static void fixTridentPose(Entity entity, EntityRenderState state) {
        if (!(entity instanceof LivingEntity living) || !living.isUsingItem()) return;
        if (!living.getUseItem().is(Items.TRIDENT)) return;

        boolean mainHand = living.getUsedItemHand() == InteractionHand.MAIN_HAND;
        boolean rightArm = mainHand == (living.getMainArm() == HumanoidArm.RIGHT);
        String field = rightArm ? "rightArmPose" : "leftArmPose";

        Object current = EntityReflect.getObjectQuiet(state, field);
        if (!tridentLogged) {
            tridentLogged = true;
            Cinema.LOGGER.info("[Cinema] trident: {} = {} (state {})", field, current, state.getClass().getSimpleName());
        }
        if (!(current instanceof Enum<?> pose)) return;
        for (Object k : pose.getDeclaringClass().getEnumConstants()) {
            String name = ((Enum<?>) k).name();
            if (name.equals("THROW_TRIDENT") || name.equals("THROW_SPEAR")) {
                EntityReflect.setObject(state, field, k);
                return;
            }
        }
    }
}