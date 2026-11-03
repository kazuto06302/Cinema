package net.kztmc.mc.cinema.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * ブロックのテクスチャのアニメーション(炎・水・溶岩・ポータルなど)を、指定した再生tick(0〜20)で固定する。
 * ゲームのtickが進んでも、固定中は、表示が変わらない。
 *
 * 26.3 の TextureAtlas は、アニメーションの「状態」の一覧(animatedTexturesStates)を持ち、毎tick、
 * cycleAnimationFrames() で、状態を1コマ進め、uploadAnimationFrames() で、画面へ送っている。
 *   固定: 各状態の「いまのコマ」を、指定のtickの位置に書き換える → uploadAnimationFrames() で画面へ送る → 一覧を空にする
 *         (空なら、毎tickの処理は何も進めず、画面へ送った値が残る)
 *   解除: 元の一覧を戻す(状態は、固定した位置から、続きを進む)
 * 内部の名前が違うときは、ログに出して、何もしない(/cinema atlas で調べられる)。
 */
public final class TextureAnimations {
    private TextureAnimations() {}

    private static final String LIST_FIELD = "animatedTexturesStates";
    private static final List<String> FRAME_FIELDS = List.of("frame", "currentFrame", "frameIndex");
    private static final List<String> SUB_FRAME_FIELDS = List.of("subFrame", "frameTicks", "ticks", "elapsed");

    private static Object atlasKey;                  // 固定したアトラス(リソースの再読み込みで別物になったら、仕切り直す)
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

    /**
     * 再生tick(0〜20)で固定する。
     * @return 固定できたら true
     */
    @SuppressWarnings("unchecked")
    public static boolean fix(int tick) {
        TextureAtlas atlas = blockAtlas();
        if (atlas == null) return false;
        if (!(EntityReflect.getObjectQuiet(atlas, LIST_FIELD) instanceof List<?> liveList)) {
            Cinema.LOGGER.warn("[Cinema] texture atlas has no '{}' list. Run /cinema atlas and send the log", LIST_FIELD);
            return false;
        }
        List<Object> live = (List<Object>) liveList;
        if (atlasKey != atlas) {   // リソースの再読み込みで、アトラスが別物になった
            atlasKey = atlas;
            original = List.of();
            fixed = false;
        }
        // 固定中は、一覧が空なので、保存しておいた元の一覧を使う
        if (!fixed) original = new ArrayList<>(live);

        // 各状態を、目標の「1tick手前」の位置にする
        int done = 0;
        for (Object state : original) {
            List<Integer> times = frameTimes(state);
            if (times.isEmpty()) continue;
            int[] before = positionBefore(times, positionAt(times, tick));
            if (writeInt(state, FRAME_FIELDS, before[0]) & writeInt(state, SUB_FRAME_FIELDS, before[1])) done++;
        }
        if (done == 0) {
            Cinema.LOGGER.warn("[Cinema] could not set any animation state ({} found). Run /cinema atlas and send the log",
                    original.size());
            return false;
        }

        // 一覧へ戻して、ゲーム自身に、1tickだけ進めさせる(状態が目標へ進み、画面へ送る印が付く)。そのあと、一覧を空にする
        live.clear();
        live.addAll(original);
        atlas.tick();
        EntityReflect.call(atlas, "uploadAnimationFrames");   // tick の中で送られない版のため(送られていれば、何も起きない)
        freeze(atlas, live);

        fixed = true;
        fixedTick = tick;
        Cinema.LOGGER.info("[Cinema] texture animation fixed at tick {}: {} of {} states", tick, done, original.size());
        return true;
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

    /** その位置の、1tick手前の位置(1tick進めると、その位置になる) */
    private static int[] positionBefore(List<Integer> times, int[] position) {
        if (position[1] > 0) return new int[] {position[0], position[1] - 1};
        int previous = Math.floorMod(position[0] - 1, times.size());
        return new int[] {previous, times.get(previous) - 1};
    }
    /** 固定を解く。元の一覧を戻す(固定した位置から、続きを進む) */
    @SuppressWarnings("unchecked")
    public static void release() {
        TextureAtlas atlas = blockAtlas();
        if (atlas != null && atlas == atlasKey && fixed) {
            Object live = EntityReflect.getObjectQuiet(atlas, LIST_FIELD);
            try {
                ((List<Object>) live).addAll(original);
            } catch (RuntimeException ex) {   // 変更できない一覧のときは、差し替える
                EntityReflect.setObject(atlas, LIST_FIELD, new ArrayList<>(original));
            }
        }
        fixed = false;
    }

    private static void freeze(TextureAtlas atlas, List<?> live) {
        try {
            live.clear();
        } catch (RuntimeException ex) {   // 変更できない一覧のときは、空の一覧に差し替える
            EntityReflect.setObject(atlas, LIST_FIELD, new ArrayList<>());
        }
    }

    // ================= 状態の書き換え =================

    /** 状態を、再生tick の位置(コマ番号と、コマの中の経過)にする。フィールドが見つからなければ false */
    private static boolean setState(Object state, int tick) {
        List<Integer> times = frameTimes(state);
        if (times.isEmpty()) return false;
        int total = times.stream().mapToInt(Integer::intValue).sum();
        int t = Math.floorMod(tick, total);

        int frame = 0;
        int sub = 0;
        int acc = 0;
        for (int i = 0; i < times.size(); i++) {
            if (t < acc + times.get(i)) {
                frame = i;
                sub = t - acc;
                break;
            }
            acc += times.get(i);
        }
        boolean a = writeInt(state, FRAME_FIELDS, frame);
        boolean b = writeInt(state, SUB_FRAME_FIELDS, sub);
        return a && b;
    }

    /** 状態の中から、名前の候補のどれかの int フィールドを持つオブジェクトを探して、書き込む */
    private static boolean writeInt(Object state, List<String> names, int value) {
        Object owner = findOwner(state, names, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (owner == null) return false;
        for (String name : names) {
            if (EntityReflect.setIntQuiet(owner, name, value)) return true;
        }
        return false;
    }

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

    // ================= コマごとの時間 =================

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

    /** /cinema atlas: アトラスと、動きの状態の内部構造を、ログに出す(名前が違うときの調査用) */
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