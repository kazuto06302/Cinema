package net.kztmc.mc.cinema.ui.panel.right;

import java.util.Locale;
import net.kztmc.mc.cinema.entity.PartAxes;
import net.kztmc.mc.cinema.entity.PartEdits.Channel;
import net.kztmc.mc.cinema.ui.core.Modifiers;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// 本体のパーツを、軸の矢印のドラッグで動かす(移動・回転・拡大)
public final class PartGizmo {
    private PartGizmo() {}

    public enum Mode { MOVE, ROTATE, SCALE }

    // 値を読み書きする相手(右パネルが実装する)
    public interface Target {
        double get(Channel channel);

        void set(Channel channel, double value);
    }

    public static Mode mode = Mode.MOVE;
    public static boolean scaleLinked = true;   // 大きさのリンク(右パネルのボタンと共有)

    private static final String[] AXES = {"X", "Y", "Z"};
    private static final double ROTATE_PER_PIXEL = 0.6;   // 回転: 1ドットあたりの度
    private static final double SCALE_PER_PIXEL = 0.5;    // 拡大: 1ドットあたりの%

    private static int axis = -1;          // ドラッグ中の軸
    private static double dirX, dirY;      // その軸の、画面上の向き(単位ベクトル)
    private static double pxPerUnit = 1;   // モデルの1ピクセルあたり、画面の何ドットか
    private static String label = "";

    public static boolean dragging() {
        return axis >= 0;
    }

    // マウスの位置に、いちばん近い軸(掴める距離にあるもの)。なければ -1
    public static int pick(String path, double mx, double my) {
        if (!PartAxes.hasData(path)) return -1;
        double[] o = ViewProjection.toScreen(PartAxes.origin());
        if (o == null) return -1;
        double best = Theme.u(9);
        int found = -1;
        for (int i = 0; i < 3; i++) {
            double[] e = ViewProjection.toScreen(PartAxes.tip(i));
            if (e == null || Math.hypot(e[0] - o[0], e[1] - o[1]) < 6) continue;   // 手前を向いて、短すぎる軸は、掴めない
            double dist = segmentDistance(mx, my, o[0], o[1], e[0], e[1]);
            if (dist < best) {
                best = dist;
                found = i;
            }
        }
        return found;
    }

    // カーソルが世界の上にあるとき、毎フレーム呼ぶ(矢印を光らせる)
    public static void hover(String path, double mx, double my, boolean overWorld) {
        if (dragging()) {
            PartAxes.hovered = axis;
        } else {
            PartAxes.hovered = overWorld ? pick(path, mx, my) : -1;
        }
    }

    // 押した: 軸を掴めたら true
    public static boolean press(String path, double mx, double my) {
        int picked = pick(path, mx, my);
        if (picked < 0) return false;
        double[] o = ViewProjection.toScreen(PartAxes.origin());
        double[] e = ViewProjection.toScreen(PartAxes.tip(picked));
        double len = Math.hypot(e[0] - o[0], e[1] - o[1]);
        dirX = (e[0] - o[0]) / len;
        dirY = (e[1] - o[1]) / len;

        // モデルの8ピクセルぶん動くと、画面で何ドット動くか(移動の換算に使う)
        double[] e8 = ViewProjection.toScreen(PartAxes.origin().add(PartAxes.perPixel(picked).scale(8)));
        pxPerUnit = e8 == null ? 1 : Math.max(0.01, Math.hypot(e8[0] - o[0], e8[1] - o[1]) / 8.0);

        axis = picked;
        label = "";
        PartAxes.hovered = picked;
        return true;
    }

    public static void drag(double dx, double dy, Target target) {
        if (axis < 0) return;
        double along = dx * dirX + dy * dirY;       // 軸の向きに動いたぶん
        double across = -dx * dirY + dy * dirX;     // 軸に垂直に動いたぶん

        switch (mode) {
            case MOVE -> {
                Channel c = Channel.position(axis);
                double v = target.get(c) + along / pxPerUnit;
                target.set(c, v);
                label = AXES[axis] + "  " + format(v) + " px";
            }
            case ROTATE -> {
                Channel c = Channel.rotation(axis);
                double v = target.get(c) + across * ROTATE_PER_PIXEL;
                target.set(c, v);
                label = AXES[axis] + "  " + format(v) + " °";
            }
            case SCALE -> {
                double delta = along * SCALE_PER_PIXEL;
                if (scaleLinked != Modifiers.shift()) {   // リンクがオンなら3軸を一緒に(Shiftで逆)
                    double v = 0;
                    for (int a = 0; a < 3; a++) {
                        Channel c = Channel.scale(a);
                        v = target.get(c) + delta;
                        target.set(c, v);
                    }
                    label = "XYZ  " + format(v) + " %";
                } else {
                    Channel c = Channel.scale(axis);
                    double v = target.get(c) + delta;
                    target.set(c, v);
                    label = AXES[axis] + "  " + format(v) + " %";
                }
            }
        }
    }

    public static void release() {
        axis = -1;
        PartAxes.hovered = -1;
    }

    // ドラッグ中、カーソルの近くに、いまの値を出す
    public static void drawOverlay(GuiGraphicsExtractor g, Font font, int mx, int my) {
        if (axis < 0 || label.isEmpty()) return;
        int pad = Theme.u(4);
        int w = Theme.textWidth(font, label) + pad * 2;
        int h = Theme.textH() + pad * 2;
        int x = mx + Theme.u(12);
        int y = my - h - Theme.u(6);
        g.nextStratum();
        g.fill(x, y, x + w, y + h, 0xF2101319);
        g.outline(x, y, w, h, 0xFF4B5568);
        Theme.text(g, font, label, x + pad, y + pad, Theme.CHANGED);
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // 点 (px, py) と、線分 (ax, ay)-(bx, by) の距離
    private static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + dx * t), py - (ay + dy * t));
    }
}