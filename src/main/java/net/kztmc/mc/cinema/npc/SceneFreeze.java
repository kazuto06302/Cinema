package net.kztmc.mc.cinema.npc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * NPCをティックさせない/描画の補間値を固定するための登録簿。
 * 実際の停止は mixin(TickRateManagerMixin / EntityRendererMixin)が参照する。
 * NPCは常に停止していて、アニメーション時刻は NpcData.animTick で指定する。
 * 補間値は、時刻の小数部分(0.1刻みの時刻を表すため)。
 */
public final class SceneFreeze {
    private SceneFreeze() {}

    // 統合サーバーのスレッドから isEntityFrozen が呼ばれることがあるので、スレッドセーフにする
    private static final Map<Entity, Float> NPC_ENTITIES = new ConcurrentHashMap<>();

    public static void register(Entity e) {
        NPC_ENTITIES.putIfAbsent(e, 0.0f);
    }

    public static void unregister(Entity e) {
        NPC_ENTITIES.remove(e);
    }

    public static void clearRegistry() {
        NPC_ENTITIES.clear();
    }

    /** 切断時に呼ぶ */
    public static void reset() {
        NPC_ENTITIES.clear();
    }

    /** 登録済みのNPCだけ、補間値を更新する(アイコン用のエンティティは対象外) */
    public static void setPartialTick(Entity e, float partialTick) {
        NPC_ENTITIES.computeIfPresent(e, (k, v) -> partialTick);
    }

    /** mixin から呼ばれる。このNPCの描画に使う補間値 */
    public static float partialTickOf(Entity e) {
        Float p = NPC_ENTITIES.get(e);
        return p == null ? 0.0f : p;
    }

    /** mixin から呼ばれる。登録済みのNPCなら常に true */
    public static boolean isFrozenNpc(Entity e) {
        return NPC_ENTITIES.containsKey(e);
    }
}