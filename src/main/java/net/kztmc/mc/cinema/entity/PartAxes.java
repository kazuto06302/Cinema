package net.kztmc.mc.cinema.entity;

import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

// 選んでいるパーツの、方向軸(世界座標)。描画のたびに更新して、矢印の描画と、ドラッグの判定で使う
public final class PartAxes {
    private PartAxes() {}

    private static final int[] COLORS = {0xFFE5534B, 0xFF57AB5A, 0xFF539BF5};   // X=赤、Y=緑、Z=青
    public static final double LENGTH = 0.35;   // 矢印の長さ(ブロック)

    private static String path;
    private static Vec3 origin;
    private static final Vec3[] perBlock = new Vec3[3];   // 各軸の、モデルの1ブロックぶんの、世界での向きと長さ
    private static long stamp;

    // いま指している(光らせている)軸。ドラッグ中、またはカーソルが近いもの。なければ -1
    public static volatile int hovered = -1;

    // 描画のスレッドで、パーツの姿勢が決まったときに呼ぶ
    public static void store(String p, Vec3 o, Vec3 x, Vec3 y, Vec3 z) {
        path = p;
        origin = o;
        perBlock[0] = x;
        perBlock[1] = y;
        perBlock[2] = z;
        stamp = System.currentTimeMillis();
    }

    // 最近の値があり、指定のパーツのものか
    public static boolean hasData(String p) {
        return origin != null && p != null && p.equals(path) && System.currentTimeMillis() - stamp < 250;
    }

    public static Vec3 origin() {
        return origin;
    }

    // 矢印の先(長さ LENGTH ブロック)
    public static Vec3 tip(int axis) {
        return origin.add(perBlock[axis].normalize().scale(LENGTH));
    }

    // モデルの1ピクセル(16分の1ブロック)ぶんの、世界での向きと長さ
    public static Vec3 perPixel(int axis) {
        return perBlock[axis].scale(1.0 / 16.0);
    }

    // 1フレームに1回、Gizmos の収集の中で呼ぶ。パーツを選んでいて、最近の値があれば、3本の矢印を描く
    public static void emit() {
        String selected = PartGlow.current();
        if (!hasData(selected)) return;
        for (int i = 0; i < 3; i++) {
            boolean hot = hovered == i;
            int color = hot ? 0xFFFFFFFF & (COLORS[i] | 0x00606060) : COLORS[i];
            Gizmos.line(origin, tip(i), color, hot ? 8.0f : 4.0f).setAlwaysOnTop();
        }
    }
}