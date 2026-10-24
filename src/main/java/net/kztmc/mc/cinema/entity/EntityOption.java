package net.kztmc.mc.cinema.entity;

import java.util.List;

/**
 * エンティティ固有の整数オプションの宣言。右パネルにスライダーまたはドロップダウンとして出る。
 * names があれば、数字の代わりに名前を表示する。dropdown が true なら、ドロップダウンで選ぶ。
 */
public record EntityOption(String key, String label, int min, int max, int def, List<String> names, boolean dropdown) {
    public EntityOption(String key, String label, int min, int max, int def, List<String> names) {
        this(key, label, min, max, def, names, false);
    }

    public EntityOption(String key, String label, int min, int max, int def) {
        this(key, label, min, max, def, List.of(), false);
    }
}