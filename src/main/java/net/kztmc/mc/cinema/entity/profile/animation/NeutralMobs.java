package net.kztmc.mc.cinema.entity.profile.animation;

import java.util.List;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * 中立モブ(オオカミ・ハチなど)の「怒っている」状態。
 * バージョンによって、怒りの管理が「残り時間」か「終了時刻(ゲーム時刻)」で違うので、両方を試す。
 */
public final class NeutralMobs {
    private NeutralMobs() {}

    /** NPCの間は怒りが続くように、十分に長い時間 */
    private static final long LONG_TICKS = 1_000_000L;

    private static boolean warned;

    public static void setAngry(Entity e, boolean angry) {
        long end = angry ? e.level().getGameTime() + LONG_TICKS : 0L;
        if (EntityReflect.invokeQuiet(e, List.of("setPersistentAngerEndTime"), end)) return;
        if (EntityReflect.invokeQuiet(e, List.of("setTimeToRemainAngry", "setRemainingPersistentAngerTime"),
                angry ? (int) LONG_TICKS : 0)) {
            return;
        }
        if (!warned) {
            warned = true;
            Cinema.LOGGER.warn("[Cinema] could not set the angry state on {}. Run /cinema fields {}",
                    e.getClass().getName(), e.getType().getDescriptionId());
        }
    }
}