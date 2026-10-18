package net.kztmc.mc.cinema.npc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * NPCをティックさせない/描画の補間値を固定するための登録簿。
 * 実際の停止は mixin(TickRateManagerMixin / EntityRendererMixin)が isFrozenNpc を見て行う。
 * NPCは常に停止していて、アニメーションの時刻は NpcData.animTick で指定する。
 */
public final class SceneFreeze {
    private SceneFreeze() {}

    /** NPCの描画に使う補間値。1.0で「ティックを進め切った位置」に固定する */
    public static final float FROZEN_PARTIAL_TICK = 1.0f;

    // 統合サーバーのスレッドから isEntityFrozen が呼ばれることがあるので、スレッドセーフにする
    private static final Set<Entity> NPC_ENTITIES = ConcurrentHashMap.newKeySet();

    public static void register(Entity e) {
        NPC_ENTITIES.add(e);
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

    /** mixin から呼ばれる。登録済みのNPCなら常に true */
    public static boolean isFrozenNpc(Entity e) {
        return NPC_ENTITIES.contains(e);
    }
}