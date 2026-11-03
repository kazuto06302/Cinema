package net.kztmc.mc.cinema.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
 *   1. NPCの描画用の状態を作り、レンダラーと同じ手順(向き・大きさ)で、モデルの全パーツの「面」を、画面へ投影する
 *      (非表示のパーツは除く)
 *   2. 各面を、画素ごとに、テクスチャの透明度を見ながら塗りつぶして、全体のシルエットにする
 *      (翼の膜のように、形が、テクスチャの透明度で決まるものも、正しくなぞれる)
 *   3. シルエットを LINE_PX 画素だけ外へ広げて、元のシルエットを引いた「輪」を、線として描く(glow と同じ、外側の位置)
 *
 * 体のモデルを取れないもの(ボート・絵画・クッションなど)は、当たり判定の箱の輪郭にする。
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

    /** シルエットの範囲が、この画素数より大きいときは、半分の解像度で塗る(計算を軽くする) */
    private static final int HALF_RESOLUTION_AREA = 900_000;

    private static final double NEAR = 0.05;

    private static boolean warned;

    // 画素の領域(毎フレーム使い回す)
    private static boolean[] mask = new boolean[0];
    private static boolean[] horizontal = new boolean[0];
    private static boolean[] grown = new boolean[0];
    private static int[] sums = new int[0];

    /** 集めた形。hulls = 不透明な凸多角形(x, y の並び)、quads = テクスチャつきの四角形(頂点4つ × x, y, u, v) */
    private static final class Scene {
        final List<float[]> hulls = new ArrayList<>();
        final List<float[]> quads = new ArrayList<>();
        TextureAlpha.Sampler sampler;

        boolean isEmpty() {
            return hulls.isEmpty() && quads.isEmpty();
        }
    }

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

        Scene scene = new Scene();
        if (!collectModel(entity, view, scene) || scene.isEmpty()) {
            scene = new Scene();
            collectBox(entity, view, scene);
        }
        if (scene.isEmpty()) return;

        // 全体の範囲(画面の内側に収める)。外へ広げる分の余白を足す
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] hull : scene.hulls) {
            for (int i = 0; i < hull.length; i += 2) {
                minX = Math.min(minX, hull[i]);
                maxX = Math.max(maxX, hull[i]);
                minY = Math.min(minY, hull[i + 1]);
                maxY = Math.max(maxY, hull[i + 1]);
            }
        }
        for (float[] quad : scene.quads) {
            for (int i = 0; i < 16; i += 4) {
                minX = Math.min(minX, quad[i]);
                maxX = Math.max(maxX, quad[i]);
                minY = Math.min(minY, quad[i + 1]);
                maxY = Math.max(maxY, quad[i + 1]);
            }
        }
        int margin = LINE_PX + 2;
        int ox = Math.max(0, (int) Math.floor(minX) - margin);
        int oy = Math.max(0, (int) Math.floor(minY) - margin);
        int ex = Math.min(view.w - 1, (int) Math.ceil(maxX) + margin);
        int ey = Math.min(view.h - 1, (int) Math.ceil(maxY) + margin);
        int realW = ex - ox + 1;
        int realH = ey - oy + 1;
        if (realW <= 2 || realH <= 2) return;

        // 範囲が大きいときは、半分の解像度で塗る
        int step = (long) realW * realH > HALF_RESOLUTION_AREA ? 2 : 1;
        int w = (realW + step - 1) / step;
        int h = (realH + step - 1) / step;
        int thickness = Math.max(1, Math.round((float) LINE_PX / step));

        int size = w * h;
        if (mask.length < size) {
            mask = new boolean[size];
            horizontal = new boolean[size];
            grown = new boolean[size];
        }
        if (sums.length < Math.max(w, h) + 1) sums = new int[Math.max(w, h) + 1];
        Arrays.fill(mask, 0, size, false);

        // 塗る(画素の領域の座標へ変換してから)
        for (float[] hull : scene.hulls) {
            float[] local = hull.clone();
            for (int i = 0; i < local.length; i += 2) {
                local[i] = (local[i] - ox) / step;
                local[i + 1] = (local[i + 1] - oy) / step;
            }
            fillPolygon(local, w, h);
        }
        for (float[] quad : scene.quads) {
            float[] local = quad.clone();
            for (int i = 0; i < 16; i += 4) {
                local[i] = (local[i] - ox) / step;
                local[i + 1] = (local[i + 1] - oy) / step;
            }
            fillTriangle(local, 0, 1, 2, scene.sampler, w, h);
            fillTriangle(local, 0, 2, 3, scene.sampler, w, h);
        }
        grow(w, h, thickness);

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
                    g.fill(ox + runStart * step, oy + y * step, ox + x * step, oy + (y + 1) * step, COLOR);
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
        for (int y = 0; y < h; y++) {
            sums[0] = 0;
            for (int x = 0; x < w; x++) sums[x + 1] = sums[x] + (mask[y * w + x] ? 1 : 0);
            for (int x = 0; x < w; x++) {
                horizontal[y * w + x] = sums[Math.min(w, x + d + 1)] - sums[Math.max(0, x - d)] > 0;
            }
        }
        for (int x = 0; x < w; x++) {
            sums[0] = 0;
            for (int y = 0; y < h; y++) sums[y + 1] = sums[y] + (horizontal[y * w + x] ? 1 : 0);
            for (int y = 0; y < h; y++) {
                grown[y * w + x] = sums[Math.min(h, y + d + 1)] - sums[Math.max(0, y - d)] > 0;
            }
        }
    }

    /** 凸多角形(不透明)を、行ごとに左右の端を求めて、塗りつぶす。座標は、画素の領域の座標 */
    private static void fillPolygon(float[] poly, int w, int h) {
        int n = poly.length / 2;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, poly[i * 2 + 1]);
            maxY = Math.max(maxY, poly[i * 2 + 1]);
        }
        int y0 = Math.max(0, (int) Math.ceil(minY - 0.5));
        int y1 = Math.min(h - 1, (int) Math.floor(maxY - 0.5));
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
            int xs = Math.max(0, (int) Math.ceil(xmin - 0.5));
            int xe = Math.min(w - 1, (int) Math.floor(xmax - 0.5));
            for (int x = xs; x <= xe; x++) mask[y * w + x] = true;
        }
    }

    /**
     * テクスチャつきの三角形を、画素ごとに、テクスチャの透明度を見ながら塗る。
     * q は、頂点ごとに (x, y, u, v) の4つが並んだ配列。a, b, c は、その頂点の番号。sampler が null なら、すべて不透明。
     */
    private static void fillTriangle(float[] q, int a, int b, int c, TextureAlpha.Sampler sampler, int w, int h) {
        float x0 = q[a * 4], y0 = q[a * 4 + 1], u0 = q[a * 4 + 2], v0 = q[a * 4 + 3];
        float x1 = q[b * 4], y1 = q[b * 4 + 1], u1 = q[b * 4 + 2], v1 = q[b * 4 + 3];
        float x2 = q[c * 4], y2 = q[c * 4 + 1], u2 = q[c * 4 + 2], v2 = q[c * 4 + 3];

        float det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(det) < 1.0e-6f) return;   // 真横から見た面(面積がない)

        float minY = Math.min(y0, Math.min(y1, y2));
        float maxY = Math.max(y0, Math.max(y1, y2));
        int ys = Math.max(0, (int) Math.ceil(minY - 0.5));
        int ye = Math.min(h - 1, (int) Math.floor(maxY - 0.5));
        float[] xs = {x0, x1, x2};
        float[] ysArr = {y0, y1, y2};

        for (int y = ys; y <= ye; y++) {
            double yc = y + 0.5;
            double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                double ay = ysArr[i], by = ysArr[j];
                if ((ay <= yc && yc < by) || (by <= yc && yc < ay)) {
                    double x = xs[i] + (yc - ay) * (xs[j] - xs[i]) / (by - ay);
                    xmin = Math.min(xmin, x);
                    xmax = Math.max(xmax, x);
                }
            }
            if (xmin > xmax) continue;
            int xStart = Math.max(0, (int) Math.ceil(xmin - 0.5));
            int xEnd = Math.min(w - 1, (int) Math.floor(xmax - 0.5));
            for (int x = xStart; x <= xEnd; x++) {
                if (sampler != null) {
                    float px = x + 0.5f;
                    float l0 = ((y1 - y2) * (px - x2) + (x2 - x1) * ((float) yc - y2)) / det;
                    float l1 = ((y2 - y0) * (px - x2) + (x0 - x2) * ((float) yc - y2)) / det;
                    float l2 = 1.0f - l0 - l1;
                    float u = l0 * u0 + l1 * u1 + l2 * u2;
                    float v = l0 * v0 + l1 * v1 + l2 * v2;
                    if (!sampler.opaque(u, v)) continue;   // 透明な画素は、塗らない
                }
                mask[y * w + x] = true;
            }
        }
    }

    // ================= 体のモデル =================

    /**
     * レンダラーの体のモデルの全パーツの「面」を、投影する。レンダラーと同じ手順で、位置・向き・大きさを反映する。
     *   生き物 : 位置 → 大きさ → 向き(setupRotations) → 反転 → 個別の大きさ(scale) → 高さの補正
     *   ドラゴン: 位置 → 飛行の履歴から決まる向きと傾き → 前後の補正 → 反転 → 高さの補正
     * 体のモデルが取れない(生き物以外のレンダラーなど)ときは、警告を出さずに false。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean collectModel(Entity e, View v, Scene scene) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        Object model = EntityReflect.getObjectQuiet(renderer, "model");
        if (model == null) return false;

        boolean dragon = renderer.getClass().getSimpleName().contains("EnderDragon");
        if (!dragon && !EntityReflect.hasMethodBefore(renderer, "setupRotations", Object.class)) return false;

        EntityRenderState state = renderer.createRenderState(e, 0.0f);   // NPCは、mixin で時刻が固定される
        PoseStack ps = new PoseStack();
        ps.translate(e.getX() - v.cx, e.getY() - v.cy, e.getZ() - v.cz);
        // 描画のときの、位置のずれ(しゃがんだプレイヤーは、2/16ブロック下がる)。レンダラーが決める
        if (EntityReflect.call(renderer, "getRenderOffset", state) instanceof Vec3 offset) {
            ps.translate(offset.x, offset.y, offset.z);
        }

        if (dragon) {
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

        scene.sampler = TextureAlpha.sampler(findTexture(renderer, state));
        traverse(part, ps, v, scene);
        return true;
    }

    /** エンダードラゴンの描画の変換。向きと傾きは、飛行の履歴(過去の位置と向きの記録)から決まる */
    private static boolean dragonTransform(Object state, PoseStack ps) {
        Object s7 = EntityReflect.call(state, "getHistoricalPos", 7);
        Object s5 = EntityReflect.call(state, "getHistoricalPos", 5);
        Object s10 = EntityReflect.call(state, "getHistoricalPos", 10);
        if (s7 == null || s5 == null || s10 == null) return false;

        float yaw = number(EntityReflect.call(s7, "yRot"));
        float climb = number(EntityReflect.call(s5, "y")) - number(EntityReflect.call(s10, "y"));
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
     * モデルのテクスチャ。レンダラーの getTextureLocation で取れればそれ。取れなければ、レンダラーの定数(ドラゴンの DRAGON_LOCATION など)から、
     * エンティティのテクスチャらしいものを探す。見つからなければ null(すべて不透明として扱う)。
     */
    private static Identifier findTexture(Object renderer, Object state) {
        if (EntityReflect.call(renderer, "getTextureLocation", state) instanceof Identifier id) return id;
        for (Class<?> c = renderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Identifier.class) continue;
                try {
                    f.setAccessible(true);
                    Identifier id = (Identifier) f.get(null);
                    String path = id.getPath();
                    if (path.contains("textures/entity") && !path.contains("beam")
                            && !path.contains("explod") && !path.contains("eyes")) {
                        return id;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 次の定数へ
                }
            }
        }
        return null;
    }

    /**
     * パーツをたどって、面を集める。描画と同じく、非表示のパーツは、子ごと飛ばす。
     * (ModelPart#visit は、非表示のパーツも通るので、使わない)
     */
    private static void traverse(ModelPart part, PoseStack ps, View v, Scene scene) {
        if (!part.visible) return;
        ps.pushPose();
        part.translateAndRotate(ps);

        if (!part.skipDraw && EntityReflect.getObjectQuiet(part, "cubes") instanceof List<?> cubes) {
            Matrix4f matrix = ps.last().pose();
            for (Object c : cubes) {
                if (c instanceof ModelPart.Cube cube) addCube(matrix, cube, v, scene);
            }
        }
        if (EntityReflect.getObjectQuiet(part, "children") instanceof Map<?, ?> children) {
            for (Object child : children.values()) {
                if (child instanceof ModelPart c) traverse(c, ps, v, scene);
            }
        }
        ps.popPose();
    }

    // ---- キューブの面(頂点とテクスチャ座標) ----

    /** キューブごとの、各面の頂点 [x, y, z, u, v] × 4(取れなければ、空)。モデルの形は変わらないので、一度だけ読む */
    private static final Map<Object, float[][]> CUBE_FACES = new WeakHashMap<>();
    private static final float[][] NO_FACES = new float[0][];

    private static float[][] faceData(Object cube) {
        if (!(EntityReflect.getObjectQuiet(cube, "polygons") instanceof Object[] polygons)) return NO_FACES;
        List<float[]> faces = new ArrayList<>();
        for (Object polygon : polygons) {
            if (!(EntityReflect.call(polygon, "vertices") instanceof Object[] vertices) || vertices.length != 4) {
                return NO_FACES;
            }
            float[] face = new float[20];
            for (int i = 0; i < 4; i++) {
                if (!readVertex(vertices[i], face, i * 5)) return NO_FACES;
            }
            faces.add(face);
        }
        return faces.toArray(new float[0][]);
    }

    /** 頂点の (x, y, z, u, v) を読む。新しい形(x() など)と、古い形(pos と u, v のフィールド)の両方に対応する */
    private static boolean readVertex(Object vertex, float[] out, int at) {
        if (EntityReflect.call(vertex, "x") instanceof Number x
                && EntityReflect.call(vertex, "y") instanceof Number y
                && EntityReflect.call(vertex, "z") instanceof Number z
                && EntityReflect.call(vertex, "u") instanceof Number u
                && EntityReflect.call(vertex, "v") instanceof Number w) {
            out[at] = x.floatValue();
            out[at + 1] = y.floatValue();
            out[at + 2] = z.floatValue();
            out[at + 3] = u.floatValue();
            out[at + 4] = w.floatValue();
            return true;
        }
        float u = EntityReflect.getFloatQuiet(vertex, "u", Float.NaN);
        float w = EntityReflect.getFloatQuiet(vertex, "v", Float.NaN);
        if (EntityReflect.getObjectQuiet(vertex, "pos") instanceof Vector3f pos && !Float.isNaN(u) && !Float.isNaN(w)) {
            out[at] = pos.x;
            out[at + 1] = pos.y;
            out[at + 2] = pos.z;
            out[at + 3] = u;
            out[at + 4] = w;
            return true;
        }
        return false;
    }

    /** キューブの各面を、投影して追加する。面の情報が読めないときは、8つの角の凸包(不透明)にする */
    private static void addCube(Matrix4f matrix, ModelPart.Cube cube, View v, Scene scene) {
        float[][] faces = CUBE_FACES.computeIfAbsent(cube, SelectionOutline::faceData);
        if (faces.length == 0) {
            addCubeHull(matrix, cube, v, scene);
            return;
        }
        Vector3f t = new Vector3f();
        for (float[] face : faces) {
            float[] quad = new float[16];
            boolean visible = true;
            for (int i = 0; i < 4 && visible; i++) {
                matrix.transformPosition(face[i * 5] / 16.0f, face[i * 5 + 1] / 16.0f, face[i * 5 + 2] / 16.0f, t);
                float[] p = v.project(t.x, t.y, t.z);
                if (p == null) {
                    visible = false;   // カメラに近すぎる面は、飛ばす
                } else {
                    quad[i * 4] = p[0];
                    quad[i * 4 + 1] = p[1];
                    quad[i * 4 + 2] = face[i * 5 + 3];
                    quad[i * 4 + 3] = face[i * 5 + 4];
                }
            }
            if (visible) scene.quads.add(quad);
        }
    }

    private static void addCubeHull(Matrix4f matrix, ModelPart.Cube cube, View v, Scene scene) {
        float[][] pts = new float[8][];
        Vector3f t = new Vector3f();
        for (int i = 0; i < 8; i++) {
            float x = ((i & 1) == 0 ? cube.minX : cube.maxX) / 16.0f;
            float y = ((i & 2) == 0 ? cube.minY : cube.maxY) / 16.0f;
            float z = ((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16.0f;
            matrix.transformPosition(x, y, z, t);
            float[] p = v.project(t.x, t.y, t.z);
            if (p == null) return;
            pts[i] = p;
        }
        float[] hull = hull(pts);
        if (hull.length >= 6) scene.hulls.add(hull);
    }

    // ================= 当たり判定の箱(体のモデルが取れないとき) =================

    private static void collectBox(Entity e, View v, Scene scene) {
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
            if (hull.length >= 6) scene.hulls.add(hull);
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