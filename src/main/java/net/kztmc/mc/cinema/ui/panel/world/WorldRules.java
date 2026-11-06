package net.kztmc.mc.cinema.ui.panel.world;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.Minecraft;

/**
 * 時間・天候の「進み」。保存先はゲームルール(ワールドに保存される)。
 *   時間: ゲームルールと、ワールドクロックの一時停止を、いつも同じ値にそろえる
 *   天候: ゲームルールのみ
 * コマンドで、どちらかが変わったら、sync() が、もう一方を合わせる。
 */
public final class WorldRules {
    private WorldRules() {}

    public enum Rule {
        TIME("ADVANCE_TIME", "DO_DAYLIGHT_CYCLE"),
        WEATHER("ADVANCE_WEATHER", "DO_WEATHER_CYCLE");

        final String[] fieldNames;

        Rule(String... fieldNames) {
            this.fieldNames = fieldNames;
        }
    }

    // 前回の sync で見えていた状態(外から変わったかを調べる)
    private static Boolean lastRule;
    private static Boolean lastPaused;

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    /** ゲームルールの定義(static フィールド)を、名前の候補から探す */
    private static Object ruleKey(Object rules, Rule rule) {
        for (Class<?> c = rules.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : rule.fieldNames) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    return f.get(null);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 次の候補へ
                }
            }
        }
        return null;
    }

    /** ゲームルールの値(進んでいるか)。読めなければ true */
    private static boolean readRule(MinecraftServer server, Rule rule) {
        Object rules = EntityReflect.call(server, "getGameRules");
        if (rules == null) return true;
        Object key = ruleKey(rules, rule);
        if (key == null) return true;
        return !(EntityReflect.call(rules, "get", key) instanceof Boolean b) || b;
    }

    /** サーバーのスレッドで呼ぶこと */
    private static void writeRule(MinecraftServer server, Rule rule, boolean running) {
        Object rules = EntityReflect.call(server, "getGameRules");
        if (rules == null) return;
        Object key = ruleKey(rules, rule);
        if (key == null) return;
        EntityReflect.call(rules, "set", key, running, server);
    }

    /** 進んでいるか。表示用 */
    public static boolean isRunning(Rule rule) {
        MinecraftServer server = server();
        return server == null || readRule(server, rule);
    }

    /** 止める/進める。時間は、ゲームルールとクロックを同時に変える */
    public static void setRunning(Rule rule, boolean running) {
        MinecraftServer server = server();
        if (server == null) return;
        if (rule == Rule.TIME) {
            lastRule = running;
            lastPaused = !running;
        }
        server.execute(() -> {
            writeRule(server, rule, running);
            if (rule == Rule.TIME) WorldTime.setPaused(server, !running);
        });
    }

    /**
     * 時間の、ゲームルールとクロックを、そろえる(毎フレーム呼んでよい。軽い)。
     *   ゲームルールが変わった → クロックを合わせる
     *   クロックが変わった(/time pause など) → ゲームルールを合わせる
     */
    public static void sync() {
        MinecraftServer server = server();
        if (server == null) {
            lastRule = null;
            lastPaused = null;
            return;
        }
        boolean rule = readRule(server, Rule.TIME);
        Boolean paused = WorldTime.isPaused(server);
        if (paused == null) {   // クロックが読めないなら、ゲームルールだけ
            lastRule = rule;
            return;
        }

        if (lastRule == null || lastPaused == null) {   // 最初は、ゲームルールを正とする
            if (paused == rule) server.execute(() -> WorldTime.setPaused(server, !rule));
            lastRule = rule;
            lastPaused = !rule;
            return;
        }

        if (rule != lastRule) {   // ゲームルールが変わった
            server.execute(() -> WorldTime.setPaused(server, !rule));
            lastRule = rule;
            lastPaused = !rule;
        } else if (paused != lastPaused) {   // クロックが変わった
            boolean running = !paused;
            server.execute(() -> writeRule(server, Rule.TIME, running));
            lastRule = running;
            lastPaused = paused;
        }
    }
}