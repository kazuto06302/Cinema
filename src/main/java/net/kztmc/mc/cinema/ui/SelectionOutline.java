package net.kztmc.mc.cinema.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
 *   2. 各キューブの投影(凸多角形)を、画素の領域へ塗りつぶして、全体のシルエットにする
 *   3. シルエットの縁(外と接する画素)だけを、線として描く
 *
 * 体のモデルを取れないもの(ボート・エンドクリスタルなど)は、当たり判定の箱の輪郭にする。
 * GUIの中で完結するので、壁越しでも見え、画面を閉じる・UIを隠すと消える(撮影には写らない)。
 * 視野角は、設定の値を使う(走ったときなどの一時的な変化は、反映しない)。
 */
public final class SelectionOutline {
    private SelectionOutline() {}

    /** 線の色 */
    private static final int COLOR = Theme.ACCENT | 0xFF000000;

    /** 線の太さ(実際の画面の画素) */
    private static final int LINE_PX = 4;

    private static final double NEAR = 0.05;

    private static boolean warned;

    // シルエットの領域(毎フレーム使い回す)
    private static boolean[] mask = new boolean[0];

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

    // ================= シルエットの形(凸多角形の集まり) =================

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

        // 全体の範囲(画面の内側に収める)
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] poly : polygons) {
            for (int i = 0; i < poly.length; i += 2) {
                minX = Math.min(minX, poly[i]);
                maxX = Math.max(maxX, poly[i]);
                minY = Math.min(minY, poly[i + 1]);
                maxY = Math.max(maxY, poly[i + 1]);
            }
        }
        int ox = Math.max(0, (int) Math.floor(minX) - 2);
        int oy = Math.max(0, (int) Math.floor(minY) - 2);
        int ex = Math.min(view.w - 1, (int) Math.ceil(maxX) + 2);
        int ey = Math.min(view.h - 1, (int) Math.ceil(maxY) + 2);
        int w = ex - ox + 1;
        int h = ey - oy + 1;
        if (w <= 2 || h <= 2) return;

        if (mask.length < w * h) mask = new boolean[w * h];
        Arrays.fill(mask, 0, w * h, false);

        for (float[] poly : polygons) fillPolygon(poly, ox, oy, w, h);

        int before = LINE_PX / 2;
        int after = LINE_PX - before;
        double guiScale = window.getGuiScale();
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / guiScale), (float) (1.0 / guiScale));
        for (int y = 0; y < h; y++) {
            int runStart = -1;
            for (int x = 0; x <= w; x++) {
                boolean boundary = x < w && isBoundary(x, y, w, h);
                if (boundary && runStart < 0) {
                    runStart = x;
                } else if (!boundary && runStart >= 0) {
                    g.fill(ox + runStart - before, oy + y - before, ox + x + after, oy + y + after, COLOR);
                    runStart = -1;
                }
            }
        }
        g.pose().popMatrix();
    }

    /** 領域の外は、塗られているものとして扱う(画面の端で切れた輪郭に、線を引かないため) */
    private static boolean set(int x, int y, int w, int h) {
        return x < 0 || y < 0 || x >= w || y >= h || mask[y * w + x];
    }

    private static boolean isBoundary(int x, int y, int w, int h) {
        return mask[y * w + x]
                && (!set(x - 1, y, w, h) || !set(x + 1, y, w, h) || !set(x, y - 1, w, h) || !set(x, y + 1, w, h));
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

    // ---- 体のモデル ----

    /**
     * レンダラーの体のモデルの全キューブを、投影する。
     * LivingEntityRenderer と同じ手順で、位置・向き・大きさを反映する(位置 → 大きさ → 向き → 反転 → 個別の大きさ → 高さの補正)。
     * 体のモデルが取れない(生き物以外のレンダラーなど)ときは false。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean collectModel(Entity e, View v, List<float[]> polygons) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        Object model = EntityReflect.getObject(renderer, "model");
        if (model == null) return false;

        EntityRenderState state = renderer.createRenderState(e, 0.0f);   // NPCは、mixin で時刻が固定される
        float scale = EntityReflect.getFloatQuiet(state, "scale", 1.0f);
        float bodyRot = EntityReflect.getFloatQuiet(state, "bodyRot", 0.0f);

        PoseStack ps = new PoseStack();
        ps.translate(e.getX() - v.cx, e.getY() - v.cy, e.getZ() - v.cz);
        ps.scale(scale, scale, scale);
        if (!EntityReflect.invokeFirst(renderer, List.of("setupRotations"), state, ps, bodyRot, scale)) return false;
        ps.scale(-1.0f, -1.0f, 1.0f);
        EntityReflect.invokeFirst(renderer, List.of("scale"), state, ps);
        ps.translate(0.0f, -1.501f, 0.0f);

        // 現在の状態でポーズを取らせて、パーツの位置・回転を確定する(描画のときにも毎回やり直されるので、影響しない)
        EntityReflect.invokeFirst(model, List.of("setupAnim"), state);
        Object root = EntityReflect.call(model, "root");
        if (!(root instanceof ModelPart part)) return false;

        part.visit(ps, (pose, path, index, cube) -> addCube(pose.pose(), cube, v, polygons));
        return true;
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

    // ---- 当たり判定の箱(体のモデルが取れないとき) ----

    private static void collectBox(Entity e, View v, List<float[]> polygons) {
        AABB box = e.getBoundingBox();
        float[][] pts = new float[8][];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX;
            double y = (i & 2) == 0 ? box.minY : box.maxY;
            double z = (i & 4) == 0 ? box.minZ : box.maxZ;
            float[] p = v.project(x - v.cx, y - v.cy, z - v.cz);
            if (p == null) return;
            pts[i] = p;
        }
        float[] hull = hull(pts);
        if (hull.length >= 6) polygons.add(hull);
    }

    // ---- 凸包 ----

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