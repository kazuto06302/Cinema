package net.kztmc.mc.cinema.entity;

import java.util.List;

/**
 * エンティティ固有の整数オプションの宣言。右パネルに、スライダー・ドロップダウン・チェックボックスのどれかで出る。
 * names があれば、数字の代わりに名前を表示する。チェックボックスは、0(オフ)と1(オン)。
 */
public record EntityOption(String key, String label, int min, int max, int def, List<String> names, Kind kind) {
    public enum Kind { SLIDER, DROPDOWN, CHECKBOX }

    public EntityOption(String key, String label, int min, int max, int def, List<String> names) {
        this(key, label, min, max, def, names, Kind.SLIDER);
    }

    public EntityOption(String key, String label, int min, int max, int def) {
        this(key, label, min, max, def, List.of(), Kind.SLIDER);
    }

    public boolean dropdown() {
        return kind == Kind.DROPDOWN;
    }
}