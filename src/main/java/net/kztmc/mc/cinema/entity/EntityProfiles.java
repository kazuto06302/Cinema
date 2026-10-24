package net.kztmc.mc.cinema.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kztmc.mc.cinema.entity.profile.*;

import net.kztmc.mc.cinema.npc.NpcPose;
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
        register(new BatProfile());
        register(new FrogProfile());
        register(new SnifferProfile());
        register(new FishProfile());
        register(new PufferfishProfile());
        register(new DolphinProfile());
        register(new TurtleProfile());
        register(new AxolotlProfile());
        register(new PhantomProfile());
        register(new EvokerProfile());
        register(new HoglinProfile());
        register(new IronGolemProfile());
        register(new SnowGolemProfile());
        register(new PandaProfile());
        register(new SheepProfile());
        register(new EndCrystalProfile());

        // ---- 腕のポーズが効くモデル(プレイヤーは種類で判定) ----
        register(EntityProfile.of(
                "minecraft:zombie",
                "minecraft:husk",
                "minecraft:drowned",
                "minecraft:zombie_villager",
                "minecraft:skeleton",
                "minecraft:stray",
                "minecraft:bogged",
                "minecraft:wither_skeleton",
                "minecraft:piglin",
                "minecraft:piglin_brute",
                "minecraft:zombified_piglin",
                "minecraft:pillager",
                "minecraft:vindicator",
                "minecraft:illusioner",
                "minecraft:giant"
        ).humanoid());

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

        // ---- 歩かないモブ: 右パネルに「歩行」を出さない ----
        for (String id : List.of(
                "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
                "minecraft:dolphin", "minecraft:squid", "minecraft:glow_squid",
                "minecraft:guardian", "minecraft:elder_guardian", "minecraft:ghast", "minecraft:happy_ghast",
                "minecraft:bat", "minecraft:phantom", "minecraft:vex", "minecraft:allay", "minecraft:bee",
                "minecraft:blaze", "minecraft:slime", "minecraft:magma_cube", "minecraft:shulker",
                "minecraft:ender_dragon", "minecraft:wither")) {
            ensure(id).noWalk();
        }

        // ---- 座れるモブ ----
        for (String id : List.of("minecraft:wolf", "minecraft:cat", "minecraft:fox", "minecraft:parrot")) {
            ensure(id).poses(NpcPose.STANDING, NpcPose.SITTING);
        }
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

    /** すでに登録があればそれを返し、なければ作って登録する(個別の設定を後から足すとき用) */
    private static EntityProfile ensure(String id) {
        EntityProfile p = BY_ID.get(id);
        if (p == null) {
            p = EntityProfile.of(id);
            register(p);
        }
        return p;
    }
}