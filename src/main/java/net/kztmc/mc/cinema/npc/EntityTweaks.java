package net.kztmc.mc.cinema.npc;

import java.lang.reflect.Field;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** 生成しただけでは見えない/動いてしまうエンティティの個別対応。 */
public final class EntityTweaks {
    private EntityTweaks() {}

    private static final String EVOKER_FANGS = "minecraft:evoker_fangs";

    /**
     * 牙のアニメーション位置。lifeTicks が小さいほど牙が大きく開く。
     * 12 で半開き。目安は 8〜14(4以下だと閉じ始めて縮む、22だとほぼ見えない)。
     */
    private static final int FANGS_HOLD_TICKS = 12;

    private static Field fangsLifeTicks;
    private static boolean fangsReflectionFailed;

    private static boolean isFangs(Entity e) {
        return EVOKER_FANGS.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    /** 生成直後に1回呼ぶ(アイコン用・NPC用の両方) */
    public static void prepare(Entity e) {
        if (!isFangs(e)) return;
        e.setSilent(true);                // 「攻撃開始」の効果音を鳴らさない
        e.handleEntityEvent((byte) 4);    // これを呼ばないと牙は何も描画されない
        hold(e);
    }

    /**
     * NPCは毎tick呼ぶ。放っておくと牙が引っ込んで消えるので、アニメーション位置を固定する。
     * アイコン用のエンティティはtickされないので、prepare だけで足りる。
     */
    public static void hold(Entity e) {
        if (fangsReflectionFailed || !isFangs(e)) return;
        try {
            if (fangsLifeTicks == null) {
                fangsLifeTicks = e.getClass().getDeclaredField("lifeTicks");
                fangsLifeTicks.setAccessible(true);
            }
            fangsLifeTicks.setInt(e, FANGS_HOLD_TICKS);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            fangsReflectionFailed = true;
            Cinema.LOGGER.warn("Could not hold evoker fangs animation (field 'lifeTicks')", ex);
        }
    }
}