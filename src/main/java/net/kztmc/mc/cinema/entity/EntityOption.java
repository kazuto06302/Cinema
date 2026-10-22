package net.kztmc.mc.cinema.entity;

import java.util.List;

/** エンティティ固有の整数オプションの宣言。右パネルにスライダーとして出る。names があれば、数字の代わりに名前を表示する。 */
public record EntityOption(String key, String label, int min, int max, int def, List<String> names) {
    public EntityOption(String key, String label, int min, int max, int def) {
        this(key, label, min, max, def, List.of());
    }
}