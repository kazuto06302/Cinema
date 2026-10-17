package net.kztmc.mc.cinema.npc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * NPCだけを止める。ワールドのほかのエンティティやサーバーには影響しない。
 * 実際の停止は、mixin 側(TickRateManagerMixin / EntityRendererMixin)が isFrozenNpc を見て行う。
 */
public final class SceneFreeze {
    private SceneFreeze() {}

    /** 止めている間、NPCの描画に使う補間値。1.0で「ティックを進め切った位置」に固定する */
    public static final float FROZEN_PARTIAL_TICK = 1.0f;

    private static volatile boolean frozen;

    // サーバースレッド(統合サーバー)から isEntityFrozen が呼ばれることがあるので、スレッドセーフにする
    private static final Set<Entity> NPC_ENTITIES = ConcurrentHashMap.newKeySet();

    public static boolean isFrozen() {
        return frozen;
    }

    public static void toggle() {
        frozen = !frozen;
    }

    public static void set(boolean value) {
        frozen = value;
    }

    /** 切断時に呼ぶ */
    public static void reset() {
        frozen = false;
        NPC_ENTITIES.clear();
    }

    // ---- NPCの登録(NpcManager が呼ぶ) ----

    public static void register(Entity e) {
        NPC_ENTITIES.add(e);
    }

    public static void unregister(Entity e) {
        NPC_ENTITIES.remove(e);
    }

    public static void clearRegistry() {
        NPC_ENTITIES.clear();
    }

    /** mixin から呼ばれる。止めている間の、登録済みNPCのときだけ true */
    public static boolean isFrozenNpc(Entity e) {
        return frozen && NPC_ENTITIES.contains(e);
    }
}