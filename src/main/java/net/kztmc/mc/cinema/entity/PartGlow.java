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

    // いま光らせるパーツのパス。なければ null(アイテムは、まだ光らせられないので、対象外)
    public static String current() {
        String p = path;
        return active && p != null && !p.startsWith("@") ? p : null;
    }
}