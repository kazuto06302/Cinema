package net.kztmc.mc.cinema.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;

/** モブ選択リスト用のカタログ。MOD追加のモブも自動で含まれる。 */
public final class MobCatalog {
    private MobCatalog() {}

    public record Entry(String id, String name) {}

    /**
     * モブ追加画面に出さないエンティティ。
     * devモード(検索欄に「cinema」と入力)の間は、これも全部表示される。
     * 出したくないモブはここにIDを足す。
     */
    private static final Set<String> HIDDEN = Set.of(
            "minecraft:marker",
            "minecraft:interaction",
            "minecraft:area_effect_cloud",
            "minecraft:block_display",
            "minecraft:item_display",
            "minecraft:text_display",
            "minecraft:item",
            "minecraft:ominous_item_spawner"
    );

    private static List<Entry> all;
    private static Map<String, Entry> byId;

    private static void ensure() {
        if (all != null) return;
        all = BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(t -> t.canSummon())
                .map(t -> new Entry(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString(),
                        t.getDescription().getString()))
                .filter(e -> !e.id().equals("minecraft:player"))
                .sorted(Comparator.comparing(Entry::name))
                .toList();
        byId = all.stream().collect(Collectors.toMap(Entry::id, Function.identity(), (a, b) -> a));
    }

    public static boolean isHidden(String id) {
        return HIDDEN.contains(id);
    }

    /** includeHidden が true なら、HIDDEN のモブも含める(devモード用) */
    public static List<Entry> filter(String query, boolean includeHidden) {
        ensure();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(e -> includeHidden || !HIDDEN.contains(e.id()))
                .filter(e -> q.isEmpty()
                        || e.name().toLowerCase(Locale.ROOT).contains(q)
                        || e.id().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public static List<Entry> filter(String query) {
        return filter(query, false);
    }

    /** 名前の引き当ては、HIDDEN に関係なく全モブが対象(NPC一覧の表示名に使う) */
    public static String nameOf(String id) {
        ensure();
        Entry e = byId.get(id);
        if (e != null) return e.name();
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}