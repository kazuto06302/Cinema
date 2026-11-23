package net.kztmc.mc.cinema.entity;

// 光らせるパーツの選択
public final class PartGlow {
    private PartGlow() {}

    // 光る色(ARGB)。変更した項目の黄色と同じ
    public static final int COLOR = 0xFFFFD34D;

    private static volatile String path;     // 選んでいるパーツ(null なら、なし)
    private static volatile boolean active;  // パーツのタブを開いているか

    public static void select(String p) {
        path = p;
    }

    public static String selected() {
        return path;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    // 光らせる対象(モデルのパーツでも、持っているアイテムでも)。なければ null
    public static String target() {
        String p = path;
        return active ? p : null;
    }

    // モデルのパーツだけ(アイテムは除く)
    public static String current() {
        String p = path;
        return active && p != null && !p.startsWith("@") ? p : null;
    }
}