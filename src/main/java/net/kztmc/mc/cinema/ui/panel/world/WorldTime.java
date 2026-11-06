package net.kztmc.mc.cinema.ui.panel.world;

import java.util.List;
import java.util.Optional;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 時刻の読み書き。26.x の時刻は、ワールドクロックで管理されている。
 *   読む(クライアント): ClientLevel#getDefaultClockTime()  ← getGameTime() は、世界ができてからの総tickで、時刻ではない
 *   書く(サーバー)    : クロックマネージャーの setTotalTicks(クロック, tick)
 *   止める           : クロックマネージャーの setPaused(クロック, boolean)
 * 26.3 で、ServerClockManager#getTotalTicks は、getInstance(clock).totalTicks() に置き換わった。
 * クラスの形が違っても、動くように、メソッドは、名前で探す(見つからなければ、ログに出して、何もしない)。
 */
public final class WorldTime {
    private WorldTime() {}

    private static final long DAY = 24000L;

    /** 表示用: クライアント側の、1日の中の時刻(0〜23999 tick。0 = 6:00) */
    public static int timeOfDay(ClientLevel level) {
        return (int) Math.floorMod(level.getDefaultClockTime(), DAY);
    }

    // ---- サーバー側 ----

    private static Object manager(ServerLevel level) {
        Object manager = EntityReflect.call(level, "clockManager");
        return manager != null ? manager : EntityReflect.call(level.getServer(), "clockManager");
    }

    /** そのディメンションの、既定のクロック(Holder<WorldClock>)。なければ null */
    private static Object clockOf(ServerLevel level) {
        Object dimension = EntityReflect.call(level, "dimensionType");
        if (dimension != null && EntityReflect.call(dimension, "defaultClock") instanceof Optional<?> clock && clock.isPresent()) {
            return clock.get();
        }
        return null;
    }

    /** いまの総tick。読めなければ -1 */
    private static long totalTicks(Object manager, Object clock) {
        if (EntityReflect.call(manager, "getTotalTicks", clock) instanceof Number n) return n.longValue();   // 26.2 以前
        Object instance = EntityReflect.call(manager, "getInstance", clock);                                  // 26.3
        if (instance != null && EntityReflect.call(instance, "totalTicks") instanceof Number n) return n.longValue();
        return -1;
    }

    /** 1日の中の時刻を、timeOfDay にする(日の数は、保つ)。サーバーのスレッドで呼ぶこと */
    public static boolean set(MinecraftServer server, int timeOfDay) {
        ServerLevel level = server.overworld();
        Object manager = manager(level);
        Object clock = clockOf(level);
        if (manager == null || clock == null) {
            warn(manager, clock);
            return false;
        }
        long current = totalTicks(manager, clock);
        long target = current < 0 ? timeOfDay : current - Math.floorMod(current, DAY) + timeOfDay;
        if (EntityReflect.invokeQuiet(manager, List.of("setTotalTicks"), clock, target)) return true;
        Cinema.LOGGER.warn("[Cinema] setTotalTicks was not found on {}. Run /cinema clock and send the log", manager.getClass().getName());
        return false;
    }

    /** 時刻が止まっているか。分からなければ null */
    public static Boolean isPaused(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Object manager = manager(level);
        Object clock = clockOf(level);
        if (manager == null || clock == null) return null;
        if (EntityReflect.call(manager, "isPaused", clock) instanceof Boolean paused) return paused;
        Object instance = EntityReflect.call(manager, "getInstance", clock);
        if (instance != null) {
            if (EntityReflect.call(instance, "isPaused") instanceof Boolean paused) return paused;
            if (EntityReflect.call(instance, "paused") instanceof Boolean paused) return paused;
        }
        return null;
    }

    /** 時刻を止める/進める。できなければ false */
    public static boolean setPaused(MinecraftServer server, boolean paused) {
        ServerLevel level = server.overworld();
        Object manager = manager(level);
        Object clock = clockOf(level);
        return manager != null && clock != null && EntityReflect.invokeQuiet(manager, List.of("setPaused"), clock, paused);
    }

    private static boolean warned;

    private static void warn(Object manager, Object clock) {
        if (warned) return;
        warned = true;
        Cinema.LOGGER.warn("[Cinema] world clock was not found (manager = {}, clock = {}). Run /cinema clock and send the log",
                manager == null ? "null" : manager.getClass().getName(), clock == null ? "null" : "found");
    }

    /** /cinema clock: 時刻まわりのメソッドを、ログに出す(名前が違うときの調査用) */
    public static void dump(MinecraftServer server) {
        ServerLevel level = server.overworld();
        EntityReflect.dumpMatching(level, "clock", "time");
        Object manager = manager(level);
        Cinema.LOGGER.info("[Cinema] clock manager: {}", manager == null ? "null" : manager.getClass().getName());
        if (manager != null) EntityReflect.dumpObject(manager);
        Object dimension = EntityReflect.call(level, "dimensionType");
        if (dimension != null) EntityReflect.dumpMatching(dimension, "clock", "time");
        Object clock = clockOf(level);
        Cinema.LOGGER.info("[Cinema] default clock: {}", clock);
        if (manager != null && clock != null) {
            Object instance = EntityReflect.call(manager, "getInstance", clock);
            if (instance != null) EntityReflect.dumpObject(instance);
        }
    }
}