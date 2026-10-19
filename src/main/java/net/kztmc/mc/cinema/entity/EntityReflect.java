package net.kztmc.mc.cinema.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.kztmc.mc.cinema.Cinema;

/** 非公開フィールドへの書き込み。見つからない・失敗したフィールドは、ログを1回出して以降は何もしない。 */
public final class EntityReflect {
    private EntityReflect() {}

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    @FunctionalInterface
    private interface Setter {
        void set(Field f) throws IllegalAccessException;
    }

    public static void setInt(Object target, String name, int value) {
        set(target, name, f -> f.setInt(target, value));
    }

    public static void setFloat(Object target, String name, float value) {
        set(target, name, f -> f.setFloat(target, value));
    }

    private static void set(Object target, String name, Setter setter) {
        String key = target.getClass().getName() + "#" + name;
        if (FAILED.contains(key)) return;
        try {
            setter.set(field(target.getClass(), name, key));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FAILED.add(key);
            Cinema.LOGGER.warn("Could not set field '{}' on {}", name, target.getClass().getName(), ex);
        }
    }

    /** 親クラスもたどってフィールドを探す(エルダーガーディアンは Guardian を継承している等) */
    private static Field field(Class<?> start, String name, String key) throws NoSuchFieldException {
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;

        Class<?> c = start;
        while (true) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                FIELDS.put(key, f);
                return f;
            } catch (NoSuchFieldException ex) {
                c = c.getSuperclass();
                if (c == null) throw ex;
            }
        }
    }

    /** 公開メソッドの呼び出し(クラスを直接参照せずに済ませる)。失敗したらログを1回出して、以降は何もしない */
    public static void invoke(Object target, String name, Class<?>[] types, Object... args) {
        String key = target.getClass().getName() + "#" + name + "()";
        if (FAILED.contains(key)) return;
        try {
            Method m = target.getClass().getMethod(name, types);
            m.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FAILED.add(key);
            Cinema.LOGGER.warn("Could not call method '{}' on {}", name, target.getClass().getName(), ex);
        }
    }
}