package net.kztmc.mc.cinema.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.EntityIds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 選択中のNPCの輪郭を、線でなぞる(自前の描画)。バニラの glow の仕組みは使わない。
 *
 *   1. NPCの描画用の状態を作り、レンダラーと同じ手順(向き・大きさ)で、モデルの全パーツのキューブを、画面へ投影する
 *      (非表示のパーツと、テクスチャがすべて透明なキューブは、除く)
 *   2. 各キューブの投影(凸多角形)を、画素の領域へ塗りつぶして、全体のシルエットにする
 *   3. シルエットを LINE_PX 画素だけ外へ広げて、元のシルエットを引いた「輪」を、線として描く(glow と同じ、外側の位置)
 *
 * 体のモデルを取れないもの(ボート・エンドクリスタルなど)は、当たり判定の箱の輪郭にする。
 * GUIの中で完結するので、壁越しでも見え、画面を閉じると消える(撮影には写らない)。
 * 視野角は、設定の値を使う(走ったときなどの一時的な変化は、反映しない)。
 */
public final class SelectionOutline {
    private SelectionOutline() {}

    /** 線の色 */
    private static final int COLOR = Theme.ACCENT | 0xFF000000;

    /**
     * 線の太さ(実際の画面の画素)。ここを変えるだけで、太さが変わる。
     * 輪郭の外側へ、この太さで描く(バニラの glow と同じ位置)。
     * 目安: 1 = 細い / 2 = ふつう / 3 = 既定 / 4 = 太い / 6 = かなり太い
     */
    private static final int LINE_PX = 3;

    private static final double NEAR = 0.05;

    private static boolean warned;

    // 画素の領域(毎フレーム使い回す)
    private static boolean[] mask = new boolean[0];
    private static boolean[] horizontal = new boolean[0];
    private static boolean[] grown = new boolean[0];
    private static int[] sums = new int[0];

    public static void draw(GuiGraphicsExtractor g, Entity entity) {
        try {
            drawOutline(g, entity);
        } catch (RuntimeException ex) {
            if (!warned) {
                warned = true;
                Cinema.LOGGER.warn("[Cinema] selection outline failed", ex);
            }
        }
    }

    // ================= 投影 =================

    /** カメラの向きと視野角から、カメラからの相対座標を、実際の画面の画素へ投影する */
    private static final class View {
        final double cx, cy, cz;
        final Vector3f right, up, forward;
        final double tanHalf, aspect;
        final int w, h;

        View(Minecraft mc, Window window) {
            Camera camera = mc.gameRenderer.mainCamera();
            Vec3 pos = camera.position();   // バージョンによっては getPosition()
            this.cx = pos.x;
            this.cy = pos.y;
            this.cz = pos.z;
            Quaternionf rotation = camera.rotation();
            this.forward = new Vector3f(0, 0, -1).rotate(rotation);
            this.up = new Vector3f(0, 1, 0).rotate(rotation);
            this.right = new Vector3f(1, 0, 0).rotate(rotation);
            this.tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
            this.w = window.getWidth();
            this.h = window.getHeight();
            this.aspect = (double) w / h;
        }

        /** カメラからの相対座標 → 画面の画素。カメラの後ろ(手前の面より近い)なら null */
        float[] project(double rx, double ry, double rz) {
            double depth = rx * forward.x + ry * forward.y + rz * forward.z;
            if (depth < NEAR) return null;
            double xr = rx * right.x + ry * right.y + rz * right.z;
            double yu = rx * up.x + ry * up.y + rz * up.z;
            double sx = w / 2.0 + (xr / depth) / (tanHalf * aspect) * (w / 2.0);
            double sy = h / 2.0 - (yu / depth) / tanHalf * (h / 2.0);
            return new float[] {(float) sx, (float) sy};
        }
    }

    // ================= 描画 =================

    private static void drawOutline(GuiGraphicsExtractor g, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        View view = new View(mc, window);

        List<float[]> polygons = new ArrayList<>();
        if (!collectModel(entity, view, polygons) || polygons.isEmpty()) {
            polygons.clear();
            collectBox(entity, view, polygons);
        }
        if (polygons.isEmpty()) return;

        // 全体の範囲(画面の内側に収める)。外へ広げる分の余白を足す
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] poly : polygons) {
            for (int i = 0; i < poly.length; i += 2) {
                minX = Math.min(minX, poly[i]);
                maxX = Math.max(maxX, poly[i]);
                minY = Math.min(minY, poly[i + 1]);
                maxY = Math.max(maxY, poly[i + 1]);
            }
        }
        int margin = LINE_PX + 2;
        int ox = Math.max(0, (int) Math.floor(minX) - margin);
        int oy = Math.max(0, (int) Math.floor(minY) - margin);
        int ex = Math.min(view.w - 1, (int) Math.ceil(maxX) + margin);
        int ey = Math.min(view.h - 1, (int) Math.ceil(maxY) + margin);
        int w = ex - ox + 1;
        int h = ey - oy + 1;
        if (w <= 2 || h <= 2) return;

        int size = w * h;
        if (mask.length < size) {
            mask = new boolean[size];
            horizontal = new boolean[size];
            grown = new boolean[size];
        }
        if (sums.length < Math.max(w, h) + 1) sums = new int[Math.max(w, h) + 1];
        Arrays.fill(mask, 0, size, false);

        for (float[] poly : polygons) fillPolygon(poly, ox, oy, w, h);
        grow(w, h, LINE_PX);

        // 広げた領域から、元のシルエットを引いた「輪」を、線として描く(実際の画素の単位で描くため、GUIの倍率を打ち消す)
        double guiScale = window.getGuiScale();
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / guiScale), (float) (1.0 / guiScale));
        for (int y = 0; y < h; y++) {
            int runStart = -1;
            for (int x = 0; x <= w; x++) {
                boolean ring = x < w && grown[y * w + x] && !mask[y * w + x];
                if (ring && runStart < 0) {
                    runStart = x;
                } else if (!ring && runStart >= 0) {
                    g.fill(ox + runStart, oy + y, ox + x, oy + y + 1, COLOR);
                    runStart = -1;
                }
            }
        }
        g.pose().popMatrix();
    }

    /**
     * mask を、四方へ d 画素ずつ広げた結果を、grown に入れる(横方向 → 縦方向の2回。累積和を使うので、d に関係なく、画素数に比例する計算)。
     */
    private static void grow(int w, int h, int d) {
        // 横方向
        for (int y = 0; y < h; y++) {
            sums[0] = 0;
            for (int x = 0; x < w; x++) sums[x + 1] = sums[x] + (mask[y * w + x] ? 1 : 0);
            for (int x = 0; x < w; x++) {
                horizontal[y * w + x] = sums[Math.min(w, x + d + 1)] - sums[Math.max(0, x - d)] > 0;
            }
        }
        // 縦方向
        for (int x = 0; x < w; x++) {
            sums[0] = 0;
            for (int y = 0; y < h; y++) sums[y + 1] = sums[y] + (horizontal[y * w + x] ? 1 : 0);
            for (int y = 0; y < h; y++) {
                grown[y * w + x] = sums[Math.min(h, y + d + 1)] - sums[Math.max(0, y - d)] > 0;
            }
        }
    }

    /** 凸多角形を、行ごとに左右の端を求めて、塗りつぶす */
    private static void fillPolygon(float[] poly, int ox, int oy, int w, int h) {
        int n = poly.length / 2;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, poly[i * 2 + 1]);
            maxY = Math.max(maxY, poly[i * 2 + 1]);
        }
        int y0 = Math.max(oy, (int) Math.ceil(minY - 0.5));
        int y1 = Math.min(oy + h - 1, (int) Math.floor(maxY - 0.5));
        for (int y = y0; y <= y1; y++) {
            double yc = y + 0.5;
            double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double ax = poly[i * 2], ay = poly[i * 2 + 1], bx = poly[j * 2], by = poly[j * 2 + 1];
                if ((ay <= yc && yc < by) || (by <= yc && yc < ay)) {
                    double x = ax + (yc - ay) * (bx - ax) / (by - ay);
                    xmin = Math.min(xmin, x);
                    xmax = Math.max(xmax, x);
                }
            }
            if (xmin > xmax) continue;
            int xs = Math.max(ox, (int) Math.ceil(xmin - 0.5));
            int xe = Math.min(ox + w - 1, (int) Math.floor(xmax - 0.5));
            for (int x = xs; x <= xe; x++) mask[(y - oy) * w + (x - ox)] = true;
        }
    }

    // ================= 体のモデル =================

    /**
     * レンダラーの体のモデルの全キューブを、投影する。レンダラーと同じ手順で、位置・向き・大きさを反映する。
     *   生き物 : 位置 → 大きさ → 向き(setupRotations) → 反転 → 個別の大きさ(scale) → 高さの補正
     *   ドラゴン: 位置 → 飛行の履歴から決まる向きと傾き → 前後の補正 → 反転 → 高さの補正
     * 体のモデルが取れないときは false。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean collectModel(Entity e, View v, List<float[]> polygons) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        Object model = EntityReflect.getObject(renderer, "model");
        if (model == null) return false;

        EntityRenderState state = renderer.createRenderState(e, 0.0f);   // NPCは、mixin で時刻が固定される
        PoseStack ps = new PoseStack();
        ps.translate(e.getX() - v.cx, e.getY() - v.cy, e.getZ() - v.cz);

        if (renderer.getClass().getSimpleName().contains("EnderDragon")) {
            if (!dragonTransform(state, ps)) return false;
        } else {
            float scale = EntityReflect.getFloatQuiet(state, "scale", 1.0f);
            float bodyRot = EntityReflect.getFloatQuiet(state, "bodyRot", 0.0f);
            ps.scale(scale, scale, scale);
            if (!EntityReflect.invokeFirst(renderer, List.of("setupRotations"), state, ps, bodyRot, scale)) return false;
            ps.scale(-1.0f, -1.0f, 1.0f);
            EntityReflect.invokeFirst(renderer, List.of("scale"), state, ps);
            ps.translate(0.0f, -1.501f, 0.0f);
        }

        // 現在の状態でポーズを取らせて、パーツの位置・回転・表示を確定する(描画のときにも毎回やり直されるので、影響しない)
        EntityReflect.invokeFirst(model, List.of("setupAnim"), state);
        Object root = EntityReflect.call(model, "root");
        if (!(root instanceof ModelPart part)) return false;

        // テクスチャ(あれば、透明なキューブを、飛ばすために使う)
        Object tex = EntityReflect.call(renderer, "getTextureLocation", state);
        Identifier texture = tex instanceof Identifier id ? id : null;

        traverse(part, ps, v, polygons, texture);
        return true;
    }

    /** エンダードラゴンの描画の変換。向きと傾きは、飛行の履歴(過去の位置と向きの記録)から決まる */
    private static boolean dragonTransform(Object state, PoseStack ps) {
        Object s7 = EntityReflect.call(state, "getHistoricalPos", 7);
        Object s5 = EntityReflect.call(state, "getHistoricalPos", 5);
        Object s10 = EntityReflect.call(state, "getHistoricalPos", 10);

        if (s7 == null || s5 == null || s10 == null) return false;

        float yaw = number(EntityReflect.call(s7, "yRot"));
        float climb = number(EntityReflect.call(s5, "y"))
                - number(EntityReflect.call(s10, "y"));

        ps.rotate(Axis.YP.rotationDegrees(-yaw));
        ps.rotate(Axis.XP.rotationDegrees(climb * 10.0f));

        ps.translate(0.0f, 0.0f, 1.0f);
        ps.scale(-1.0f, -1.0f, 1.0f);
        ps.translate(0.0f, -1.501f, 0.0f);

        return true;
    }

    private static float number(Object value) {
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    /**
     * パーツをたどって、キューブを集める。描画と同じく、非表示のパーツは、子ごと飛ばす。
     * (ModelPart#visit は、非表示のパーツも通るので、使わない)
     */
    private static void traverse(ModelPart part, PoseStack ps, View v, List<float[]> polygons, Identifier texture) {
        if (!part.visible) return;
        ps.pushPose();
        part.translateAndRotate(ps);

        if (!part.skipDraw && EntityReflect.getObject(part, "cubes") instanceof List<?> cubes) {
            Matrix4f matrix = ps.last().pose();
            for (Object c : cubes) {
                if (c instanceof ModelPart.Cube cube && (texture == null || hasTexels(cube, texture))) {
                    addCube(matrix, cube, v, polygons);
                }
            }
        }
        if (EntityReflect.getObject(part, "children") instanceof Map<?, ?> children) {
            for (Object child : children.values()) {
                if (child instanceof ModelPart c) traverse(c, ps, v, polygons, texture);
            }
        }
        ps.popPose();
    }

    // ---- 透明なキューブの判定 ----

    /** キューブごとの、各面のテクスチャの範囲 [minU, minV, maxU, maxV](取れなければ、空) */
    private static final Map<Object, float[][]> UV_RECTS = new WeakHashMap<>();
    private static final float[][] NO_UV = new float[0][];

    /** キューブの、どれかの面が、不透明な画素を使っていれば true。範囲が取れないときも true(囲う) */
    private static boolean hasTexels(ModelPart.Cube cube, Identifier texture) {
        float[][] rects = UV_RECTS.computeIfAbsent(cube, SelectionOutline::uvRects);
        if (rects.length == 0) return true;
        for (float[] r : rects) {
            if (TextureAlpha.hasOpaque(texture, r[0], r[1], r[2], r[3])) return true;
        }
        return false;
    }

    private static float[][] uvRects(Object cube) {
        if (!(EntityReflect.getObject(cube, "polygons") instanceof Object[] polygons)) return NO_UV;
        float[][] rects = new float[polygons.length][];
        for (int i = 0; i < polygons.length; i++) {
            if (!(EntityReflect.call(polygons[i], "vertices") instanceof Object[] vertices) || vertices.length == 0) {
                return NO_UV;
            }
            float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (Object vertex : vertices) {
                if (!(EntityReflect.call(vertex, "u") instanceof Number u)
                        || !(EntityReflect.call(vertex, "v") instanceof Number w)) {
                    return NO_UV;
                }
                minU = Math.min(minU, u.floatValue());
                maxU = Math.max(maxU, u.floatValue());
                minV = Math.min(minV, w.floatValue());
                maxV = Math.max(maxV, w.floatValue());
            }
            rects[i] = new float[] {minU, minV, maxU, maxV};
        }
        return rects;
    }

    /** キューブの8つの角を投影して、その凸包(輪郭)を、多角形として追加する */
    private static void addCube(Matrix4f matrix, ModelPart.Cube cube, View v, List<float[]> polygons) {
        float[][] pts = new float[8][];
        Vector3f t = new Vector3f();
        for (int i = 0; i < 8; i++) {
            float x = ((i & 1) == 0 ? cube.minX : cube.maxX) / 16.0f;
            float y = ((i & 2) == 0 ? cube.minY : cube.maxY) / 16.0f;
            float z = ((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16.0f;
            matrix.transformPosition(x, y, z, t);
            float[] p = v.project(t.x, t.y, t.z);
            if (p == null) return;   // カメラに近すぎるキューブは、飛ばす
            pts[i] = p;
        }
        float[] hull = hull(pts);
        if (hull.length >= 6) polygons.add(hull);
    }

    // ================= 当たり判定の箱(体のモデルが取れないとき) =================

    private static void collectBox(Entity e, View v, List<float[]> polygons) {
        // 本体と、パーツ(エンダードラゴンの頭・首・体・翼・尾)の箱を、すべて足す
        List<Entity> boxes = new ArrayList<>();
        boxes.add(e);
        boxes.addAll(Arrays.asList(EntityIds.subEntities(e)));

        for (Entity part : boxes) {
            AABB box = part.getBoundingBox();
            float[][] pts = new float[8][];
            boolean visible = true;
            for (int i = 0; i < 8 && visible; i++) {
                double x = (i & 1) == 0 ? box.minX : box.maxX;
                double y = (i & 2) == 0 ? box.minY : box.maxY;
                double z = (i & 4) == 0 ? box.minZ : box.maxZ;
                float[] p = v.project(x - v.cx, y - v.cy, z - v.cz);
                if (p == null) visible = false;
                else pts[i] = p;
            }
            if (!visible) continue;
            float[] hull = hull(pts);
            if (hull.length >= 6) polygons.add(hull);
        }
    }

    // ================= 凸包 =================

    private static float cross(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** 点の集まりの凸包(Andrew のモノトーンチェーン)。x, y の並びで返す */
    private static float[] hull(float[][] points) {
        float[][] sorted = points.clone();
        Arrays.sort(sorted, (a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        float[][] h = new float[sorted.length * 2][];
        int k = 0;
        for (float[] p : sorted) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], p) <= 0) k--;
            h[k++] = p;
        }
        for (int i = sorted.length - 2, t = k + 1; i >= 0; i--) {
            float[] p = sorted[i];
            while (k >= t && cross(h[k - 2], h[k - 1], p) <= 0) k--;
            h[k++] = p;
        }
        int n = Math.max(0, k - 1);
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            out[i * 2] = h[i][0];
            out[i * 2 + 1] = h[i][1];
        }
        return out;
    }
}