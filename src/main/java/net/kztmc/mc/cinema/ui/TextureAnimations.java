package net.kztmc.mc.cinema.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * ブロックのテクスチャのアニメーション(炎・水・溶岩・ポータルなど)を、指定した再生tick(0〜20)で固定する。
 * ゲームのtickが進んでも、固定中は、表示が変わらない。
 *
 * アトラス(TextureAtlas)は、アニメーションするスプライトの「動き」のリスト(animatedTextures)を持ち、
 * 毎tick、tick() の中で、リストの動きを1コマずつ進めて、画面へ送っている。
 *   固定: 新しい動きを作って、指定のtickまで進めてから、リストを空にする(空なら、tick() は、何も進めない)
 *   解除: 進めた動きを、リストへ戻す
 * バージョンによって、内部の名前が違う可能性があるので、見つからないときは、ログに出して、何もしない(/cinema atlas で調べられる)。
 */
public final class TextureAnimations {
    private TextureAnimations() {}

    /** 動きの1周の長さが分からないときの、予備の長さ(tick)。この場合、指定のtickは、正確にならない */
    private static final int UNKNOWN_CYCLE = 256;

    private static Object atlasKey;          // 固定したアトラス(リソースの再読み込みで別物になったら、仕切り直す)
    private static List<Object> own = List.of();   // 自前で作って、進めた動き
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

    /** そのアトラスの、アニメーションするスプライト(名前 → スプライト)の一覧 */
    private static Collection<?> sprites(Object atlas) {
        if (EntityReflect.call(atlas, "getTextures") instanceof Map<?, ?> map) return map.values();
        if (EntityReflect.getObjectQuiet(atlas, "texturesByName") instanceof Map<?, ?> map) return map.values();
        return List.of();
    }

    private static void setTickers(Object atlas, List<?> list) {
        EntityReflect.setObject(atlas, "animatedTextures", list);
    }

    /**
     * 再生tick(0〜20)で固定する。動きを作り直して、進めるので、少し時間がかかる(スライダーを止めてから、実行するとよい)。
     * @return 固定できたら true
     */
    public static boolean fix(int tick) {
        TextureAtlas atlas = blockAtlas();
        if (atlas == null) return false;
        if (!(EntityReflect.getObjectQuiet(atlas, "animatedTextures") instanceof List<?>)) {
            Cinema.LOGGER.warn("[Cinema] texture atlas has no 'animatedTextures' list. Run /cinema atlas and send the log");
            return false;
        }
        if (atlasKey != atlas) {   // リソースの再読み込みで、アトラスが別物になった
            atlasKey = atlas;
            own = List.of();
            fixed = false;
        }

        // 前回作った動きは、閉じる(GPUの資源を持つバージョンがあるため)
        for (Object old : own) EntityReflect.call(old, "close");

        // 新しい動き(最初のコマから)を、アニメーションするすべてのスプライトに作って、1周の長さごとにまとめる
        List<Object> all = new ArrayList<>();
        Map<Integer, List<Object>> groups = new TreeMap<>();
        int known = 0;
        for (Object sprite : sprites(atlas)) {
            Object ticker = EntityReflect.call(sprite, "createTicker");
            if (ticker == null) continue;   // アニメーションしないスプライト
            all.add(ticker);
            int cycle = cycleLength(ticker);
            if (cycle > 0) known++;
            groups.computeIfAbsent(cycle > 0 ? cycle : UNKNOWN_CYCLE, k -> new ArrayList<>()).add(ticker);
        }
        if (all.isEmpty()) {
            Cinema.LOGGER.warn("[Cinema] no animated sprites were found in the block atlas. Run /cinema atlas and send the log");
            return false;
        }

        // 1周まわして、最初のコマを画面へ送ってから、指定のtickだけ進める(同じ長さのものは、まとめて、アトラスのtickを回す)
        for (Map.Entry<Integer, List<Object>> group : groups.entrySet()) {
            setTickers(atlas, group.getValue());
            int count = group.getKey() + tick;
            for (int i = 0; i < count; i++) atlas.tick();
        }

        setTickers(atlas, List.of());   // 固定: ゲームのtickが進んでも、何も進まない
        own = all;
        fixed = true;
        fixedTick = tick;
        Cinema.LOGGER.info("[Cinema] texture animation fixed at tick {}: {} sprites ({} with a known cycle)",
                tick, all.size(), known);
        return true;
    }

    /** 固定を解く。進めた動きを、アトラスへ戻す(画面の内容と、動きの状態が、そろったまま、続きから動く) */
    public static void release() {
        TextureAtlas atlas = blockAtlas();
        if (atlas != null && atlas == atlasKey && !own.isEmpty()) {
            setTickers(atlas, own);
        }
        fixed = false;
    }

    // ================= 動きの1周の長さ =================

    /** 動きの中から、コマの一覧(frames)を探して、時間(time)の合計を返す。見つからなければ -1 */
    private static int cycleLength(Object ticker) {
        return findFrames(ticker, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static int findFrames(Object o, int depth, Set<Object> seen) {
        if (o == null || depth > 3 || !seen.add(o)) return -1;

        if (EntityReflect.getObjectQuiet(o, "frames") instanceof List<?> frames && !frames.isEmpty()) {
            int sum = 0;
            for (Object frame : frames) {
                int time = frameTime(frame);
                if (time <= 0) {
                    sum = -1;
                    break;
                }
                sum += time;
            }
            if (sum > 0) return sum;
        }

        // 中の部品(動きの情報など)を、探す。スプライトやテクスチャ本体は、たどらない
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                String type = f.getType().getName();
                if (type.startsWith("java.") || type.endsWith("TextureAtlasSprite") || type.endsWith("SpriteContents")
                        || type.endsWith("NativeImage") || type.endsWith("TextureAtlas")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    int found = findFrames(f.get(o), depth + 1, seen);
                    if (found > 0) return found;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 次のフィールドへ
                }
            }
        }
        return -1;
    }

    private static int frameTime(Object frame) {
        if (EntityReflect.call(frame, "time") instanceof Number n) return n.intValue();
        if (EntityReflect.getObjectQuiet(frame, "time") instanceof Number n) return n.intValue();
        return -1;
    }

    // ================= 診断 =================

    /** /cinema atlas: アトラスと、動きの内部構造を、ログに出す(名前が違うときの調査用) */
    public static void dump() {
        TextureAtlas atlas = blockAtlas();
        if (atlas == null) {
            Cinema.LOGGER.info("[Cinema] block atlas not found");
            return;
        }
        EntityReflect.dumpObject(atlas);
        for (Object sprite : sprites(atlas)) {
            Object ticker = EntityReflect.call(sprite, "createTicker");
            if (ticker == null) continue;
            Cinema.LOGGER.info("[Cinema] sample sprite: {}", sprite);
            EntityReflect.dumpObject(ticker);
            Cinema.LOGGER.info("[Cinema] cycle length: {}", cycleLength(ticker));
            EntityReflect.call(ticker, "close");
            break;
        }
    }
}