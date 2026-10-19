package net.kztmc.mc.cinema.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kztmc.mc.cinema.entity.profile.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** エンティティIDから設定を引く登録簿。足すエンティティは registerAll に1行ずつ書く。 */
public final class EntityProfiles {
    private EntityProfiles() {}

    private static final Map<String, EntityProfile> BY_ID = new HashMap<>();
    private static final EntityProfile DEFAULT = EntityProfile.of();

    static {
        registerAll();
    }

    private static void registerAll() {
        // ---- 動きのあるもの ----
        register(new EvokerFangsProfile());
        register(new GuardianProfile());
        register(new SquidProfile());
        register(new ChickenProfile());
        register(new ParrotProfile());
        register(new CreeperProfile());
        register(new ShulkerProfile());
        register(new SlimeProfile());
        register(new EnderDragonProfile());

        // ---- 何も映らない: モブ追加画面に出さず、アイコンも描かない ----
        for (String id : List.of(
                "minecraft:marker",
                "minecraft:interaction",
                "minecraft:area_effect_cloud",
                "minecraft:block_display",
                "minecraft:item_display",
                "minecraft:text_display",
                "minecraft:item",
                "minecraft:ominous_item_spawner"
        )) {
            register(EntityProfile.of(id).hidden().noRender());
        }

        // ---- 選択画面には出すが、アイコンは描かない ----
        register(EntityProfile.of("minecraft:lightning_bolt").noRender());

        // ↓ ここに足していく
    }

    public static void register(EntityProfile profile) {
        for (String id : profile.ids()) {
            BY_ID.put(id, profile);
        }
    }

    /** 登録が無いエンティティには、何も設定していない既定の設定が返る */
    public static EntityProfile of(String id) {
        return BY_ID.getOrDefault(id, DEFAULT);
    }

    public static EntityProfile of(Entity e) {
        return of(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }
}