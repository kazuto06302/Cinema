package net.kztmc.mc.cinema.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.kztmc.mc.cinema.npc.NpcData;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

// NPC のパーツごとの編集(位置・回転・大きさ・表示)
//   保存: NPC のオプションに「part.<パス>.<項目>」で入る(値は100倍の整数)
//   描画: モデルの姿勢が作られたあと、描かれる直前に、パーツへ足す
public final class PartEdits {
    private PartEdits() {}

    // 持っているアイテムの、特別なパス(モデルのパーツではない)
    public static final String ITEM_MAIN = "@item_main";
    public static final String ITEM_OFF = "@item_off";

    private static final String PREFIX = "part.";

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

    // モデルの姿勢を作る直前: この描画に、編集があるか覚える
    public static void beginModel(Object state) {
        current = state instanceof EntityRenderState es ? es.getData(KEY) : null;
    }

    public static void endModel() {
        current = null;
    }

    // 描かれる直前: 覚えた編集を、パーツに足す
    public static void applyCurrent(Model model) {
        Map<String, PartTransform> edits = current;
        if (edits == null) return;
        ModelPart root = model.root();
        for (Map.Entry<String, PartTransform> e : edits.entrySet()) {
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
            if (!t.visible()) part.visible = false;
            applied++;
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
}