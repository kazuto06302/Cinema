package net.kztmc.mc.cinema.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

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
    private static final Map<String, List<Field>> MATCHED = new HashMap<>();
    private static final Set<Class<?>> DUMPED = new HashSet<>();

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
    public static boolean setFloatsMatching(Object target, float value, List<String> anyOfA, List<String> anyOfB) {
        Class<?> cls = target.getClass();
        String key = cls.getName() + "#match" + anyOfA + anyOfB;

        List<Field> fields = MATCHED.get(key);
        if (fields == null) {
            fields = new ArrayList<>();
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() != float.class || Modifier.isStatic(f.getModifiers())) continue;
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (containsAny(n, anyOfA) && containsAny(n, anyOfB)) {
                        try {
                            f.setAccessible(true);
                            fields.add(f);
                        } catch (RuntimeException ex) {
                            Cinema.LOGGER.warn("Cannot access field {}", f, ex);
                        }
                    }
                }
            }
            MATCHED.put(key, fields);

            if (fields.isEmpty()) {
                Cinema.LOGGER.warn("[Cinema] no float field matched {} x {} on {}. Related fields: {}",
                        anyOfA, anyOfB, cls.getName(), related(cls, anyOfA));
            } else {
                List<String> names = new ArrayList<>();
                for (Field f : fields) names.add(f.getDeclaringClass().getSimpleName() + "." + f.getName());
                Cinema.LOGGER.info("[Cinema] writing {} on {}: {}", anyOfA, cls.getSimpleName(), names);
            }
        }

        for (Field f : fields) {
            try {
                f.setFloat(target, value);
            } catch (IllegalAccessException | RuntimeException ignored) {
                // 次回以降も同じ失敗になるので、ここでは何もしない
            }
        }
        return !fields.isEmpty();
    }

    private static boolean containsAny(String lowerName, List<String> keywords) {
        for (String k : keywords) {
            if (lowerName.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** 診断用: キーワードのどれかを名前に含む、あらゆる型のフィールドの一覧("宣言クラス.名前(型)") */
    private static List<String> related(Class<?> start, List<String> keywords) {
        List<String> out = new ArrayList<>();
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (containsAny(f.getName().toLowerCase(Locale.ROOT), keywords)) {
                    out.add(c.getSimpleName() + "." + f.getName() + "(" + f.getType().getSimpleName() + ")");
                }
            }
        }
        return out;
    }

    public static Object getObject(Object target, String name) {
        String key = target.getClass().getName() + "#" + name;
        String getKey = key + "#get";
        if (FAILED.contains(getKey)) return null;
        try {
            return field(target.getClass(), name, key).get(target);
        } catch (NoSuchFieldException ex) {
            FAILED.add(getKey);
            Cinema.LOGGER.warn("[Cinema] field '{}' not found on {}", name, target.getClass().getName());
            return null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FAILED.add(getKey);
            Cinema.LOGGER.warn("[Cinema] could not read field '{}' on {}", name, target.getClass().getName(), ex);
            return null;
        }
    }

    public static boolean setBoolean(Object target, String name, boolean value) {
        return set(target, name, boolean.class, f -> f.setBoolean(target, value));
    }

    /** 診断用: 全フィールドの名前・型・現在値を、クラスごとに1回だけログに出す */
    public static void dumpOnce(Object target) {
        Class<?> cls = target.getClass();
        if (!DUMPED.add(cls)) return;
        List<String> out = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String v;
                try {
                    f.setAccessible(true);
                    v = String.valueOf(f.get(target));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    v = "?";
                }
                out.add(c.getSimpleName() + "." + f.getName() + "(" + f.getType().getSimpleName() + ")=" + v);
            }
        }
        Cinema.LOGGER.warn("[Cinema] fields of {}: {}", cls.getName(), out);
    }

    /** オブジェクト型のフィールドへの書き込み(null も可) */
    public static boolean setObject(Object target, String name, Object value) {
        return set(target, name, Object.class, f -> f.set(target, value));
    }

    private static final Set<String> STOP = Set.of(
            "net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.Mob",
            "net.minecraft.world.entity.Entity", "java.lang.Object");

    private static void warnOnce(String key, String message, Object... args) {
        if (FAILED.add(key)) Cinema.LOGGER.warn(message, args);
    }

    /** int フィールドの値。見つからなければ fallback(ログは出さない) */
    public static int getIntQuiet(Object target, String name, int fallback) {
        try {
            return field(target.getClass(), name, target.getClass().getName() + "#" + name).getInt(target);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return fallback;
        }
    }

    /** long フィールドへの書き込み。版によって型や名前が違うので、見つからなくてもログは出さない */
    public static boolean setLongQuiet(Object target, String name, long value) {
        try {
            field(target.getClass(), name, target.getClass().getName() + "#" + name).setLong(target, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** 名前の候補を順に試して、最初に見つかった boolean フィールドに書き込む。全部無ければ警告を1回出す */
    public static boolean setBooleanFirst(Object target, boolean value, String... names) {
        for (String name : names) {
            try {
                field(target.getClass(), name, target.getClass().getName() + "#" + name).setBoolean(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // 次の候補へ
            } catch (ReflectiveOperationException | RuntimeException ex) {
                warnOnce(target.getClass().getName() + "#bool:" + name, "[Cinema] could not write '{}'", name, ex);
                return false;
            }
        }
        warnOnce(target.getClass().getName() + "#bool:" + List.of(names),
                "[Cinema] none of the boolean fields {} were found on {}", List.of(names), target.getClass().getName());
        return false;
    }

    /** 名前の候補を順に試して、最初に見つかったメソッドを呼ぶ(非公開でもよい)。全部無ければ、候補の一覧つきで警告を1回出す */
    public static boolean invokeFirst(Object target, List<String> names, Object... args) {
        for (String name : names) {
            Boolean r = tryInvoke(target, name, args);
            if (r != null) return r;
        }
        warnOnce(target.getClass().getName() + "#none:" + names,
                "[Cinema] none of the methods {} were found on {}. Declared methods there: {}",
                names, target.getClass().getName(), methodNames(target.getClass()));
        return false;
    }

    /** 引数が enum のメソッドを、定数名で呼ぶ(enum のクラスを直接参照しなくて済む) */
    public static boolean invokeWithEnumArg(Object target, String name, String constantName) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1 || !m.getParameterTypes()[0].isEnum()) continue;
                for (Object k : m.getParameterTypes()[0].getEnumConstants()) {
                    if (((Enum<?>) k).name().equals(constantName)) {
                        try {
                            m.setAccessible(true);
                            m.invoke(target, k);
                            return true;
                        } catch (ReflectiveOperationException | RuntimeException ex) {
                            warnOnce(target.getClass().getName() + "#enum:" + name, "[Cinema] call to {} failed", name, ex);
                            return false;
                        }
                    }
                }
            }
        }
        warnOnce(target.getClass().getName() + "#enum:" + name + constantName,
                "[Cinema] method {}({}) was not found on {}", name, constantName, target.getClass().getName());
        return false;
    }

    /** 診断用(/cinema fields): LivingEntity より下のクラスの、全フィールド(現在値つき)とメソッドをログに出す */
    public static void dumpEntity(Object e) {
        Cinema.LOGGER.info("[Cinema] ===== {} =====", e.getClass().getName());
        for (Class<?> c = e.getClass(); c != null && !STOP.contains(c.getName()); c = c.getSuperclass()) {
            List<String> fields = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String v;
                try {
                    f.setAccessible(true);
                    v = String.valueOf(f.get(e));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    v = "?";
                }
                fields.add(f.getName() + "(" + f.getType().getSimpleName() + ")=" + v);
            }
            List<String> methods = new ArrayList<>();
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && !m.isSynthetic()) {
                    methods.add(m.getName() + "/" + m.getParameterCount());
                }
            }
            Cinema.LOGGER.info("[Cinema] {} fields: {}", c.getSimpleName(), fields);
            Cinema.LOGGER.info("[Cinema] {} methods: {}", c.getSimpleName(), methods);
        }
    }

    /** 見つかって呼べたら TRUE、見つかったが失敗したら FALSE、見つからなければ null */
    private static Boolean tryInvoke(Object target, String name, Object[] args) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                if (!accepts(m.getParameterTypes(), args)) continue;
                try {
                    m.setAccessible(true);
                    m.invoke(target, args);
                    return Boolean.TRUE;
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    warnOnce(target.getClass().getName() + "#call:" + name,
                            "[Cinema] call to {} failed on {}", name, target.getClass().getName(), ex);
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Object a = args[i];
            if (a == null) {
                if (types[i].isPrimitive()) return false;
            } else if (!wrap(types[i]).isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == boolean.class) return Boolean.class;
        if (t == int.class) return Integer.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == long.class) return Long.class;
        if (t == byte.class) return Byte.class;
        if (t == short.class) return Short.class;
        return Character.class;
    }

    private static List<String> methodNames(Class<?> start) {
        List<String> out = new ArrayList<>();
        for (Class<?> c = start; c != null && !STOP.contains(c.getName()); c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && !m.isSynthetic()) {
                    out.add(c.getSimpleName() + "." + m.getName() + "/" + m.getParameterCount());
                }
            }
        }
        return out;
    }
}