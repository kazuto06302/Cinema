package net.kztmc.mc.cinema.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.locale.Language;

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
        return EntityProfiles.of(id).isHidden();
    }

    /** includeHidden が true なら、非表示のモブも含める(devモード用) */
    public static List<Entry> filter(String query, boolean includeHidden) {
        ensure();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(e -> includeHidden || !isHidden(e.id()))
                .filter(e -> q.isEmpty()
                        || e.name().toLowerCase(Locale.ROOT).contains(q)
                        || e.id().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public static List<Entry> filter(String query) {
        return filter(query, false);
    }

    /** 名前の引き当ては、HIDDEN に関係なく全モブが対象(NPC一覧の表示名に使う) */
    public static String nameOf(String entityTypeId) {
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) return entityTypeId;

        String custom = "cinema.mob." + id.getNamespace() + "." + id.getPath();
        if (Language.getInstance().has(custom)) return I18n.get(custom);

        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .map(type -> type.getDescription().getString())
                .orElse(entityTypeId);
    }
}