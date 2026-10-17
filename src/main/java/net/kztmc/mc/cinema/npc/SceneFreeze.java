package net.kztmc.mc.cinema.npc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.TickRateManager;

/** /tick freeze 相当を、クライアントだけで行う(サーバーは止めない)。 */
public final class SceneFreeze {
    private SceneFreeze() {}

    private static boolean frozen;
    private static boolean weFroze;   // 自分が止めたときだけ、解除で動かす

    public static boolean isFrozen() {
        return frozen;
    }

    public static void toggle() {
        set(!frozen);
    }

    public static void set(boolean value) {
        frozen = value;
        apply(Minecraft.getInstance());
    }

    /** 切断時に呼ぶ */
    public static void reset() {
        frozen = false;
        weFroze = false;
    }

    /** サーバーからの更新やワールド切り替えで解除されても、止めたい間は再適用する */
    public static void tick(Minecraft mc) {
        if (frozen) apply(mc);
    }

    private static void apply(Minecraft mc) {
        if (mc.level == null) return;
        TickRateManager manager = mc.level.tickRateManager();
        if (frozen) {
            if (manager.runsNormally()) {
                manager.setFrozen(true);
                weFroze = true;
            }
        } else if (weFroze) {
            manager.setFrozen(false);
            weFroze = false;
        }
    }
}