package net.kztmc.mc.cinema.ui.panel.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * ブロックのテクスチャのアニメーション(炎・水・溶岩・ポータルなど)を、指定した再生tick(0〜20)で固定する。
 *
 * 26.3 の TextureAtlas は、アニメーションの「状態」の一覧(animatedTexturesStates)を持ち、毎tick進めて、画面へ送っている。
 * この一覧は不変リストに差し替えられることがあるため、中身を操作せず、フィールドごと差し替える。
 *   固定: 各状態の「いまのコマ」を、指定のtickの位置に書き換え、isDirty を立てる
 *         → 一覧を戻して uploadAnimationFrames() で画面へ送る → 一覧を空にする
 *   解除: 元の一覧を戻す(固定した位置から、続きを進む)
 * 状態ごとの解析(コマの時間・書き込み先)は、1回だけ行って、キャッシュする。
 */
public final class TextureAnimations {
    private TextureAnimations() {}

    private static final String LIST_FIELD = "animatedTexturesStates";
    private static final List<String> FRAME_FIELDS = List.of("frame", "currentFrame", "frameIndex");
    private static final List<String> SUB_FRAME_FIELDS = List.of("subFrame", "frameTicks", "ticks", "elapsed");

    /** 状態1つの解析結果 */
    private record Cached(List<Integer> times, Object frameOwner, Object subOwner) {}

    private static final Map<Object, Cached> CACHE = new IdentityHashMap<>();
    private static Method uploadMethod;

    private static Object atlasKey;                     // 固定したアトラス(リソースの再読み込みで別物になったら、仕切り直す)
    private static List<Object> original = List.of();   // 固定する前の、状態の一覧
    private static boolean fixed;
    private static int fixedTick;

    public static boolean isFixed() {
        return fixed;
    }

    public static int fixedTick() {
        return fixedTick;
    }

    private static TextureAtlas blockAtlas() {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        return texture instanceof TextureAtlas atlas ? atlas : null;
    }

    /** 一覧(フィールド)ごと差し替える */
    private static void setList(TextureAtlas atlas, List<?> value) {
        try {
            Field f = TextureAtlas.class.getDeclaredField(LIST_FIELD);
            f.setAccessible(true);
            f.set(atlas, value);
        } catch (Throwable t) {
            Cinema.LOGGER.warn("[Cinema] could not set {}", LIST_FIELD, t);
        }
    }

    private static Cached analyze(Object state) {
        List<Integer> times = frameTimes(state);
        Object frameOwner = findOwner(state, FRAME_FIELDS, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        Object subOwner = findOwner(state, SUB_FRAME_FIELDS, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        return new Cached(times, frameOwner, subOwner);
    }

    private static boolean writeTo(Object owner, List<String> names, int value) {
        if (owner == null) return false;
        for (String name : names) {
            if (EntityReflect.setIntQuiet(owner, name, value)) return true;
        }
        return false;
    }

    /**
     * 再生tick(0〜20)で固定する。固定中に呼べば、そのtickへ動かす。
     * @return 固定できたら true
     */
    public static boolean fix(int tick) {
        TextureAtlas atlas = blockAtlas();
        if (atlas == null) return false;
        if (atlasKey != atlas) {   // リソースの再読み込みで、アトラスが別物になった
            atlasKey = atlas;
            original = List.of();
            fixed = false;
            CACHE.clear();
        }
        if (!fixed) {
            if (!(EntityReflect.getObjectQuiet(atlas, LIST_FIELD) instanceof List<?> liveList)) {
                Cinema.LOGGER.warn("[Cinema] texture atlas has no '{}' list. Run /cinema atlas and send the log", LIST_FIELD);
                return false;
            }
            original = new ArrayList<Object>(liveList);   // 固定中は一覧が空なので、固定する前にだけ読む
        }
        if (original.isEmpty()) {
            Cinema.LOGGER.warn("[Cinema] no animation states found");
            return false;
        }

        // 各状態を、目標の位置にして、「描き直す」印を付ける
        int done = 0;
        for (Object state : original) {
            Cached c = CACHE.computeIfAbsent(state, TextureAnimations::analyze);
            if (c.times().isEmpty()) continue;
            int[] at = positionAt(c.times(), tick);
            if (writeTo(c.frameOwner(), FRAME_FIELDS, at[0]) & writeTo(c.subOwner(), SUB_FRAME_FIELDS, at[1])) {
                EntityReflect.setBooleanFirst(state, true, "isDirty");
                done++;
            }
        }
        if (done == 0) {
            Cinema.LOGGER.warn("[Cinema] could not set any animation state ({} found)", original.size());
            return false;
        }

        // 一覧を元に戻して、アップロードを1回だけ走らせ、また空にする
        setList(atlas, new ArrayList<>(original));
        try {
            if (uploadMethod == null) {
                uploadMethod = TextureAtlas.class.getDeclaredMethod("uploadAnimationFrames");
                uploadMethod.setAccessible(true);
            }
            uploadMethod.invoke(atlas);
        } catch (Throwable t) {
            Cinema.LOGGER.warn("[Cinema] uploadAnimationFrames failed", t);
        } finally {
            setList(atlas, List.of());
        }

        if (!fixed) Cinema.LOGGER.info("[Cinema] texture animation fixed at tick {}: {} of {} states", tick, done, original.size());
        fixed = true;
        fixedTick = tick;
        return true;
    }

    /** 固定を解く。元の一覧を戻す(固定した位置から、続きを進む) */
    public static void release() {
        TextureAtlas atlas = blockAtlas();
        if (atlas != null && atlas == atlasKey && fixed) {
            setList(atlas, new ArrayList<>(original));
        }
        fixed = false;
    }

    /** 再生tick の位置 {コマ番号, コマの中の経過} */
    private static int[] positionAt(List<Integer> times, int tick) {
        int total = times.stream().mapToInt(Integer::intValue).sum();
        int t = Math.floorMod(tick, total);
        int acc = 0;
        for (int i = 0; i < times.size(); i++) {
            if (t < acc + times.get(i)) return new int[] {i, t - acc};
            acc += times.get(i);
        }
        return new int[] {0, 0};
    }

    // ================= 状態の探索(解析時に1回だけ) =================

    private static Object findOwner(Object o, List<String> names, int depth, Set<Object> seen) {
        if (o == null || depth > 3 || !seen.add(o)) return null;
        for (String name : names) {
            if (hasIntField(o, name)) return o;
        }
        for (Object child : children(o)) {
            Object found = findOwner(child, names, depth + 1, seen);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean hasIntField(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name) && f.getType() == int.class && !Modifier.isStatic(f.getModifiers())) return true;
            }
        }
        return false;
    }

    /** 中の部品(動きの情報など)。スプライトやテクスチャ本体は、たどらない */
    private static List<Object> children(Object o) {
        List<Object> out = new ArrayList<>();
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                String type = f.getType().getName();
                if (type.startsWith("java.") || type.endsWith("TextureAtlasSprite") || type.endsWith("SpriteContents")
                        || type.endsWith("NativeImage") || type.endsWith("TextureAtlas") || type.contains("Gpu")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object value = f.get(o);
                    if (value != null) out.add(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 次のフィールドへ
                }
            }
        }
        return out;
    }

    /** 状態の中から、コマの一覧(frames)を探して、コマごとの時間(time)を返す。見つからなければ空 */
    private static List<Integer> frameTimes(Object state) {
        return findFrames(state, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static List<Integer> findFrames(Object o, int depth, Set<Object> seen) {
        if (o == null || depth > 3 || !seen.add(o)) return List.of();
        if (EntityReflect.getObjectQuiet(o, "frames") instanceof List<?> frames && !frames.isEmpty()) {
            List<Integer> times = new ArrayList<>();
            for (Object frame : frames) {
                int time = frameTime(frame);
                if (time <= 0) {
                    times.clear();
                    break;
                }
                times.add(time);
            }
            if (!times.isEmpty()) return times;
        }
        for (Object child : children(o)) {
            List<Integer> found = findFrames(child, depth + 1, seen);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    private static int frameTime(Object frame) {
        if (EntityReflect.call(frame, "time") instanceof Number n) return n.intValue();
        if (EntityReflect.getObjectQuiet(frame, "time") instanceof Number n) return n.intValue();
        return -1;
    }

    // ================= 診断 =================

    /** /cinema atlas: アトラスと、動きの状態の内部構造を、ログに出す */
    public static void dump() {
        TextureAtlas atlas = blockAtlas();
        if (atlas == null) {
            Cinema.LOGGER.info("[Cinema] block atlas not found");
            return;
        }
        EntityReflect.dumpObject(atlas);
        List<?> states = EntityReflect.getObjectQuiet(atlas, LIST_FIELD) instanceof List<?> l ? l : List.of();
        List<?> shown = fixed ? original : states;
        Cinema.LOGGER.info("[Cinema] animation states: {} (fixed = {})", shown.size(), fixed);
        if (shown.isEmpty()) return;

        Object state = shown.get(0);
        EntityReflect.dumpObject(state);
        int count = 0;
        for (Object child : children(state)) {
            EntityReflect.dumpObject(child);
            if (++count >= 3) break;
        }
        Cinema.LOGGER.info("[Cinema] frame times of the first state: {}", frameTimes(state));
    }
}