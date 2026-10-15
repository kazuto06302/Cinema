package net.kztmc.mc.cinema.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;

/** モブ選択リスト用のカタログ。MOD追加のモブも自動で含まれる。 */
public final class MobCatalog {
    private MobCatalog() {}

    public record Entry(String id, String name) {}

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

    public static List<Entry> filter(String query) {
        ensure();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all;
        return all.stream()
                .filter(e -> e.name().toLowerCase(Locale.ROOT).contains(q)
                        || e.id().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public static String nameOf(String id) {
        ensure();
        Entry e = byId.get(id);
        if (e != null) return e.name();
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }
}