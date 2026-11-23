package net.kztmc.mc.cinema.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.kztmc.mc.cinema.npc.NpcData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// NPC のパーツごとの編集(位置・回転・大きさ・表示)
//   保存: NPC のオプションに「part.<パス>.<項目>」で入る(値は100倍の整数)
//   描画: モデルの姿勢が作られたあと、描かれる直前に、パーツへ足す
public final class PartEdits {
    private PartEdits() {}

    // 輪郭の描画専用の状態に付ける、「このパーツだけ描く」という指定(パスの文字列)
    public static final RenderStateDataKey<String> GLOW_KEY = RenderStateDataKey.create(() -> "cinema:part_glow");

    private static String currentGlow;

    // 描画のあいだだけ変えたパーツの、元の状態(描き終わったら戻す)
    private record Saved(ModelPart part, boolean visible, boolean skipDraw) {}
    private static final java.util.List<Saved> saved = new java.util.ArrayList<>();

    // 持っているアイテムの、特別なパス(モデルのパーツではない)
    public static final String ITEM_MAIN = "@item_main";
    public static final String ITEM_OFF = "@item_off";

    private static final String PREFIX = "part.";

    // 選択中の NPC の描画の情報に付ける印(方向軸を描く対象かどうか)
    public static final RenderStateDataKey<Boolean> SELECTED_KEY = RenderStateDataKey.create(() -> "cinema:part_selected");

    private static boolean currentSelected;

    // 編集できる項目
    public enum Channel {
        PX("px", 0), PY("py", 0), PZ("pz", 0),            // 位置のずれ(ピクセル。16で1ブロック)
        RX("rx", 0), RY("ry", 0), RZ("rz", 0),            // 回転の足し算(度)
        SX("sx", 100), SY("sy", 100), SZ("sz", 100),      // 大きさ(%)
        VISIBLE("vis", 1);                                // 表示(1 = 表示、0 = 非表示)

        public final String id;
        public final double def;

        Channel(String id, double def) {
            this.id = id;
            this.def = def;
        }

        public static Channel position(int axis) {
            return values()[axis];
        }

        public static Channel rotation(int axis) {
            return values()[3 + axis];
        }

        public static Channel scale(int axis) {
            return values()[6 + axis];
        }

        public static Channel byId(String id) {
            for (Channel c : values()) {
                if (c.id.equals(id)) return c;
            }
            return null;
        }
    }

    // 1つのパーツの、まとまった編集(描画で使う)
    public record PartTransform(float px, float py, float pz, float rx, float ry, float rz,
                                float sx, float sy, float sz, boolean visible) {
        boolean isIdentity() {
            return px == 0 && py == 0 && pz == 0 && rx == 0 && ry == 0 && rz == 0
                    && sx == 100 && sy == 100 && sz == 100 && visible;
        }
    }

    // 描画の情報(EntityRenderState)に持たせる、編集の一覧
    public static final RenderStateDataKey<Map<String, PartTransform>> KEY =
            RenderStateDataKey.create(() -> "cinema:parts");

    private static final Map<Entity, Map<String, PartTransform>> BY_ENTITY =
            Collections.synchronizedMap(new WeakHashMap<>());

    // 描画のフックが、実際にパーツを動かした回数(つながっているかの確認用)
    public static volatile int applied;

    // ---- 保存と読み込み ----

    public static String key(String path, Channel channel) {
        return PREFIX + path + "." + channel.id;
    }

    public static int encode(double value) {
        return (int) Math.round(value * 100);
    }

    public static double decode(int stored) {
        return stored / 100.0;
    }

    // いまの値(保存がなければ、既定値)
    public static double get(NpcData d, String path, Channel channel) {
        return decode(d.option(key(path, channel), encode(channel.def)));
    }

    // 既定値と違う項目が、1つでもあるか
    public static boolean isEdited(NpcData d, String path) {
        for (Channel c : Channel.values()) {
            if (Math.abs(get(d, path, c) - c.def) > 1e-9) return true;
        }
        return false;
    }

    // どこかのパーツに、編集があるか
    public static boolean hasAnyEdit(NpcData d) {
        return !parse(d).isEmpty();
    }

    // path が null なら全部、指定があれば、そのパーツだけを、既定値に戻した NpcData
    public static NpcData reset(NpcData d, String path) {
        NpcData result = d;
        for (String key : new java.util.ArrayList<>(d.style().options().keySet())) {
            if (!key.startsWith(PREFIX)) continue;
            if (path != null && !key.startsWith(PREFIX + path + ".")) continue;
            Channel channel = Channel.byId(key.substring(key.lastIndexOf('.') + 1));
            if (channel != null) result = result.withOption(key, encode(channel.def));
        }
        return result;
    }

    // NPC のオプションから、パーツごとの編集を作る(既定値のままのパーツは、含めない)
    public static Map<String, PartTransform> parse(NpcData d) {
        Map<String, double[]> values = new HashMap<>();
        for (Map.Entry<String, Integer> e : d.style().options().entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(PREFIX)) continue;
            int dot = key.lastIndexOf('.');
            if (dot <= PREFIX.length()) continue;
            Channel channel = Channel.byId(key.substring(dot + 1));
            if (channel == null) continue;
            String path = key.substring(PREFIX.length(), dot);
            values.computeIfAbsent(path, p -> defaults())[channel.ordinal()] = decode(e.getValue());
        }
        Map<String, PartTransform> out = new HashMap<>();
        for (Map.Entry<String, double[]> e : values.entrySet()) {
            double[] v = e.getValue();
            PartTransform t = new PartTransform((float) v[0], (float) v[1], (float) v[2],
                    (float) v[3], (float) v[4], (float) v[5], (float) v[6], (float) v[7], (float) v[8], v[9] >= 0.5);
            if (!t.isIdentity()) out.put(e.getKey(), t);
        }
        return out;
    }

    private static double[] defaults() {
        Channel[] channels = Channel.values();
        double[] out = new double[channels.length];
        for (int i = 0; i < channels.length; i++) out[i] = channels[i].def;
        return out;
    }

    // ---- NPC と描画の情報をつなぐ ----

    // NPC を作り直したときに呼ぶ
    public static void set(Entity entity, NpcData d) {
        Map<String, PartTransform> edits = parse(d);
        if (edits.isEmpty()) BY_ENTITY.remove(entity);
        else BY_ENTITY.put(entity, edits);
    }

    // 描画の情報を作った直後に呼ぶ
    public static void attach(Entity entity, EntityRenderState state) {
        Map<String, PartTransform> edits = BY_ENTITY.get(entity);
        if (edits != null) state.setData(KEY, edits);
    }

    // ---- 描画(Mixin から呼ぶ) ----

    // 描画のスレッドだけで使う
    private static Map<String, PartTransform> current;

    // モデルの姿勢を作る直前: この描画の、編集と、光らせるパーツを覚える
    public static void beginModel(Object state) {
        if (state instanceof EntityRenderState es) {
            current = es.getData(KEY);
            currentGlow = es.getData(GLOW_KEY);
            currentSelected = es.getData(SELECTED_KEY) != null;
        } else {
            current = null;
            currentGlow = null;
            currentSelected = false;
        }
    }

    // 描き終わった後: 変えたパーツを、元に戻して、覚えたものを忘れる
    public static void endModel() {
        for (Saved s : saved) {
            s.part().visible = s.visible();
            s.part().skipDraw = s.skipDraw();
        }
        saved.clear();
        current = null;
        currentGlow = null;
        currentSelected = false;
    }

    private static void remember(ModelPart part) {
        saved.add(new Saved(part, part.visible, part.skipDraw));
    }

    public static void applyCurrent(Model model, PoseStack pose) {
        String axisPath = currentSelected ? PartGlow.current() : null;
        if (current == null && currentGlow == null) return;
        ModelPart root = model.root();

        if (current != null) {
            for (Map.Entry<String, PartTransform> e : current.entrySet()) {
                if (e.getKey().startsWith("@")) continue;   // アイテムは、別の経路
                ModelPart part = find(root, e.getKey());
                if (part == null) continue;                 // このモデルに、無いパーツ(防具など)は飛ばす
                PartTransform t = e.getValue();
                part.x += t.px();
                part.y += t.py();
                part.z += t.pz();
                part.xRot += (float) Math.toRadians(t.rx());
                part.yRot += (float) Math.toRadians(t.ry());
                part.zRot += (float) Math.toRadians(t.rz());
                part.xScale *= t.sx() / 100f;
                part.yScale *= t.sy() / 100f;
                part.zScale *= t.sz() / 100f;
                if (!t.visible()) {
                    remember(part);
                    part.visible = false;
                }
                applied++;
            }
        }

        if (currentGlow != null) isolate(root, currentGlow.split("/"), 0);
        if (axisPath != null) captureAxes(root, axisPath, pose);
    }

    // 選んだパーツと、その子だけを残す(経路の途中のパーツは、自分の形を描かず、子だけ描く)
    private static void isolate(ModelPart part, String[] names, int index) {
        if (index >= names.length) return;   // 選んだパーツ以下は、そのまま描く
        remember(part);
        part.skipDraw = true;
        if (!(EntityReflect.getObjectQuiet(part, "children") instanceof Map<?, ?> children)) return;
        for (Map.Entry<?, ?> e : children.entrySet()) {
            if (!(e.getValue() instanceof ModelPart child)) continue;
            if (String.valueOf(e.getKey()).equals(names[index])) {
                isolate(child, names, index + 1);
            } else {
                remember(child);
                child.visible = false;
            }
        }
    }

    // 「body/right_arm」のようなパスで、パーツを探す
    public static ModelPart find(ModelPart root, String path) {
        ModelPart current = root;
        for (String name : path.split("/")) {
            if (!current.hasChild(name)) return null;
            current = current.getChild(name);
        }
        return current;
    }


    // 選んだパーツの、位置と軸の向き(世界座標)を求める。根から、親のパーツまでの姿勢を順に掛けて、
    // 最後のパーツは、位置のずれだけを掛ける(軸は、位置のずれが動く向き=親の向き)
    private static void captureAxes(ModelPart root, String path, PoseStack pose) {
        Matrix4f m = new Matrix4f(pose.last().pose());
        compose(m, root, true);
        ModelPart part = root;
        String[] names = path.split("/");
        for (int i = 0; i < names.length; i++) {
            if (!part.hasChild(names[i])) return;
            part = part.getChild(names[i]);
            compose(m, part, i < names.length - 1);
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();   // 姿勢は、カメラからの相対なので、足す
        Vector3f o = m.transformPosition(0, 0, 0, new Vector3f());
        PartAxes.store(path, new Vec3(o.x + cam.x, o.y + cam.y, o.z + cam.z),
                axis(m, 1, 0, 0), axis(m, 0, 1, 0), axis(m, 0, 0, 1));
    }

    // パーツの姿勢を掛ける(withChildren が true なら、子に効く、回転と大きさも)
    private static void compose(Matrix4f m, ModelPart p, boolean withChildren) {
        m.translate(p.x / 16f, p.y / 16f, p.z / 16f);
        if (!withChildren) return;
        if (p.xRot != 0 || p.yRot != 0 || p.zRot != 0) m.rotateZYX(p.zRot, p.yRot, p.xRot);
        m.scale(p.xScale, p.yScale, p.zScale);
    }

    // モデルの1ブロックぶんが、世界で、どの向き・長さになるか(正規化しない)
    private static Vec3 axis(Matrix4f m, float x, float y, float z) {
        Vector3f d = m.transformDirection(x, y, z, new Vector3f());
        return new Vec3(d.x, d.y, d.z);
    }

    // 選択中の NPC の描画の情報に、印を付ける(方向軸を描く対象)
    public static void markSelected(EntityRenderState state) {
        state.setData(SELECTED_KEY, Boolean.TRUE);
    }

    // 持っているアイテムの編集を、描く直前のポーズに足す(アイテムの向きの座標系で)。false なら、描かない
    public static boolean applyItem(EntityRenderState state, boolean mainHand, PoseStack pose) {
        Map<String, PartTransform> edits = state.getData(KEY);
        if (edits == null) return true;
        PartTransform t = edits.get(mainHand ? ITEM_MAIN : ITEM_OFF);
        if (t == null) return true;
        if (!t.visible()) return false;

        pose.translate(t.px() / 16f, t.py() / 16f, t.pz() / 16f);
        pose.rotate(new org.joml.Quaternionf().rotationZYX(
                (float) Math.toRadians(t.rz()), (float) Math.toRadians(t.ry()), (float) Math.toRadians(t.rx())));
        pose.scale(t.sx() / 100f, t.sy() / 100f, t.sz() / 100f);
        return true;
    }
}