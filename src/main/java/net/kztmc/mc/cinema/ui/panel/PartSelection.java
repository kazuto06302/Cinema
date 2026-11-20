package net.kztmc.mc.cinema.ui.panel;

import net.kztmc.mc.cinema.entity.PartGlow;

// 選んでいるパーツ(左のパーツ一覧と、右の調整で共有する)
public final class PartSelection {
    private PartSelection() {}

    private static String selected;

    public static String selected() {
        return selected;
    }

    // 選ぶ(選んだパーツが、本体で光る)。null で、選択を外す
    public static void select(String path) {
        selected = path;
        PartGlow.select(path);
    }
}