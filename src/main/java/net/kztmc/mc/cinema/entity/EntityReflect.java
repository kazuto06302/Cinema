package net.kztmc.mc.cinema.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kztmc.mc.cinema.Cinema;

/**
 * 非公開・名前が不確かなフィールドやメソッドへの書き込み。
 * 失敗はログを1回だけ出して、以降は何もしない。フィールドが見つからないときは、
 * 同じ型のフィールドの一覧もログに出す(正しい名前を探す手がかりにする)。
 */
public final class EntityReflect {
    private EntityReflect() {}

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    @FunctionalInterface
    private interface Setter {
        void set(Field f) throws IllegalAccessException;
    }

    public static boolean setInt(Object target, String name, int value) {
        return set(target, name, int.class, f -> f.setInt(target, value));
    }

    public static boolean setFloat(Object target, String name, float value) {
        return set(target, name, float.class, f -> f.setFloat(target, value));
    }

    /** 名前の候補を順に試して、最初に見つかったフィールドに書き込む。全部見つからなければ、ログに候補の一覧を出す */
    public static boolean trySetFloat(Object target, float value, String... names) {
        String groupKey = target.getClass().getName() + "#" + String.join("|", names);
        if (FAILED.contains(groupKey)) return false;
        for (String name : names) {
            try {
                String key = target.getClass().getName() + "#" + name;
                field(target.getClass(), name, key).setFloat(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // 次の候補へ
            } catch (ReflectiveOperationException | RuntimeException ex) {
                FAILED.add(groupKey);
                Cinema.LOGGER.warn("Could not set field '{}' on {}", name, target.getClass().getName(), ex);
                return false;
            }
        }
        FAILED.add(groupKey);
        Cinema.LOGGER.warn("None of the fields {} were found on {}. float fields there: {}",
                List.of(names), target.getClass().getName(), candidates(target.getClass(), float.class));
        return false;
    }

    private static boolean set(Object target, String name, Class<?> type, Setter setter) {
        String key = target.getClass().getName() + "#" + name;
        if (FAILED.contains(key)) return false;
        try {
            setter.set(field(target.getClass(), name, key));
            return true;
        } catch (NoSuchFieldException ex) {
            FAILED.add(key);
            Cinema.LOGGER.warn("Field '{}' not found on {}. {} fields there: {}",
                    name, target.getClass().getName(), type.getSimpleName(), candidates(target.getClass(), type));
            return false;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FAILED.add(key);
            Cinema.LOGGER.warn("Could not set field '{}' on {}", name, target.getClass().getName(), ex);
            return false;
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

    /** 診断用: 指定した型の、static でないフィールドの一覧("宣言クラス.名前") */
    private static List<String> candidates(Class<?> start, Class<?> type) {
        List<String> out = new ArrayList<>();
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == type && !Modifier.isStatic(f.getModifiers())) {
                    out.add(c.getSimpleName() + "." + f.getName());
                }
            }
        }
        return out;
    }
}