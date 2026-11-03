package net.kztmc.mc.cinema.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * 時間・天候の「進み」(ゲームルール)。シングルプレイの統合サーバーを、直接操作する(コマンドは使わない)。
 *   TIME    : advance_time    (旧: doDaylightCycle)
 *   WEATHER : advance_weather (旧: doWeatherCycle)
 * ゲームルールの形は、バージョンで違うので、ルールの定数を名前で探して、新しい形(rules.get / rules.set)と、
 * 古い形(rules.getBoolean / rules.getRule(...).set)の両方を試す。見つからなければ、ログに出して、何もしない。
 */
public final class WorldRules {
    private WorldRules() {}

    public enum Rule {
        TIME("ADVANCE_TIME", "RULE_DAYLIGHT", "DAYLIGHT_CYCLE"),
        WEATHER("ADVANCE_WEATHER", "RULE_WEATHER_CYCLE", "WEATHER_CYCLE");

        final String[] names;

        Rule(String... names) {
            this.names = names;
        }
    }

    private static final Map<Rule, Object> KEYS = new EnumMap<>(Rule.class);
    private static boolean warned;

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static Object rules(MinecraftServer server) {
        Object rules = EntityReflect.call(server, "getGameRules");
        if (rules == null) rules = EntityReflect.call(server.overworld(), "getGameRules");
        return rules;
    }

    /** ルールの定数(ゲームルールのクラスの static フィールド)を、名前で探す */
    private static Object key(Object rules, Rule rule) {
        Object cached = KEYS.get(rule);
        if (cached != null) return cached;
        for (Class<?> c = rules.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                for (String name : rule.names) {
                    if (!f.getName().equals(name)) continue;
                    try {
                        f.setAccessible(true);
                        Object value = f.get(null);
                        if (value != null) {
                            KEYS.put(rule, value);
                            return value;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // 次の候補へ
                    }
                }
            }
        }
        return null;
    }

    private static void warnOnce(Object rules) {
        if (warned) return;
        warned = true;
        Cinema.LOGGER.warn("[Cinema] game rule constants were not found on {}. Run /cinema rules and send the log",
                rules == null ? "null" : rules.getClass().getName());
    }

    /** 進んでいるか(止めていなければ true)。読めないときも true */
    public static boolean isRunning(Rule rule) {
        MinecraftServer server = server();
        if (server == null) return true;
        if (rule == Rule.TIME) {
            Boolean paused = WorldTime.isPaused(server);
            if (paused != null) return !paused;   // クロックの状態が読めれば、それを使う
        }
        Object rules = rules(server);
        Object key = rules == null ? null : key(rules, rule);
        if (key == null) {
            warnOnce(rules);
            return true;
        }
        if (EntityReflect.call(rules, "get", key) instanceof Boolean value) return value;
        if (EntityReflect.call(rules, "getBoolean", key) instanceof Boolean value) return value;
        Object holder = EntityReflect.call(rules, "getRule", key);
        if (holder != null && EntityReflect.call(holder, "get") instanceof Boolean value) return value;
        return true;
    }

    public static void setRunning(Rule rule, boolean running) {
        MinecraftServer server = server();
        if (server == null) return;
        server.execute(() -> {
            if (rule == Rule.TIME && WorldTime.setPaused(server, !running)) return;
            Object rules = rules(server);
            Object key = rules == null ? null : key(rules, rule);
            if (key == null) {
                warnOnce(rules);
                return;
            }
            if (EntityReflect.invokeQuiet(rules, List.of("set"), key, running, server)) return;
            Object holder = EntityReflect.call(rules, "getRule", key);
            if (holder != null) EntityReflect.invokeQuiet(holder, List.of("set"), running, server);
        });
    }

    /** /cinema rules: ゲームルールのクラスの構造を、ログに出す(名前が違うときの調査用) */
    public static void dump() {
        MinecraftServer server = server();
        if (server == null) {
            Cinema.LOGGER.info("[Cinema] no integrated server");
            return;
        }
        Object rules = rules(server);
        if (rules == null) {
            Cinema.LOGGER.info("[Cinema] getGameRules was not found on the server");
            return;
        }
        EntityReflect.dumpObject(rules);
        for (Class<?> c = rules.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            StringBuilder names = new StringBuilder();
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) names.append(f.getName()).append(' ');
            }
            Cinema.LOGGER.info("[Cinema] {} static fields: {}", c.getSimpleName(), names);
        }
    }
}