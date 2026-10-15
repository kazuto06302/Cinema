package net.kztmc.mc.cinema.npc;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public final class NpcManager {
    private static final List<NpcData> DEFS = new ArrayList<>();
    private static final Map<UUID, Entity> LIVE = new HashMap<>();

    private static ClientLevel liveLevel;
    private static HolderLookup.Provider registries;
    private static String worldKey;
    private static boolean dirty;
    private static int saveCooldown;
    private static int nextEntityId = 0x40000000;

    private NpcManager() {}

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            registries = handler.registryAccess();
            worldKey = NpcStorage.worldKey(client);
            DEFS.clear();
            DEFS.addAll(NpcStorage.load(worldKey, registries));
            LIVE.clear();
            liveLevel = null;
            Cinema.LOGGER.info("Loaded {} NPC(s) for {}", DEFS.size(), worldKey);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            saveNow();
            LIVE.clear();
            DEFS.clear();
            liveLevel = null;
            worldKey = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(NpcManager::tick);
    }

    // ---- public API ----

    public static List<NpcData> defs() {
        return List.copyOf(DEFS);
    }

    public static void add(NpcData data) {
        DEFS.add(data);
        dirty = true;
    }

    /** 定義を差し替えてエンティティも作り直す(GUIからの編集用) */
    public static void update(NpcData data) {
        for (int i = 0; i < DEFS.size(); i++) {
            if (DEFS.get(i).id().equals(data.id())) {
                DEFS.set(i, data);
                despawn(data.id());
                dirty = true;
                return;
            }
        }
    }

    public static void remove(UUID id) {
        DEFS.removeIf(d -> d.id().equals(id));
        despawn(id);
        dirty = true;
    }

    public static void clear() {
        for (NpcData d : List.copyOf(DEFS)) despawn(d.id());
        DEFS.clear();
        dirty = true;
    }

    public static Entity liveEntity(UUID id) {
        return LIVE.get(id);
    }

    // ---- tick ----

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || worldKey == null) return;

        if (level != liveLevel) { // ディメンション移動/再接続でレベルが別物になった
            LIVE.clear();
            liveLevel = level;
        }

        String dim = level.dimension().identifier().toString();
        for (NpcData def : DEFS) {
            if (!def.dimension().equals(dim)) {
                despawn(def.id());
                continue;
            }
            Entity e = LIVE.get(def.id());
            if (e != null && !e.isRemoved()) continue;

            int cx = SectionPos.blockToSectionCoord(def.x());
            int cz = SectionPos.blockToSectionCoord(def.z());
            if (!level.hasChunk(cx, cz)) continue; // チャンクが来たら出す
            spawn(level, def);
        }

        if (dirty && --saveCooldown <= 0) {
            saveNow();
        }
        if (dirty && saveCooldown < 0) saveCooldown = 40; // 2秒デバウンス
    }

    private static void saveNow() {
        if (worldKey != null && registries != null) {
            NpcStorage.save(worldKey, registries, DEFS);
        }
        dirty = false;
        saveCooldown = 0;
    }

    // ---- spawn / despawn ----

    private static void spawn(ClientLevel level, NpcData def) {
        Entity e = createEntity(level, def);
        if (e == null) return;
        e.setId(nextEntityId++);
        applyState(e, def);
        level.addEntity(e);
        LIVE.put(def.id(), e);
    }

    private static void despawn(UUID id) {
        Entity e = LIVE.remove(id);
        if (e != null && liveLevel != null) {
            liveLevel.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static Entity createEntity(ClientLevel level, NpcData def) {
        if (NpcData.PLAYER.equals(def.kind())) {
            // TODO(skin): GameProfile だけだと既定スキンになる。スキン反映は次のステップで対応
            GameProfile profile = new GameProfile(def.id(), def.skin().orElse("NPC"));
            return new RemotePlayer(level, profile);
        }
        Identifier typeId = Identifier.tryParse(def.entityType());
        Optional<EntityType<?>> type = typeId == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);

        if (type.isEmpty()) {
            Cinema.LOGGER.warn("Unknown entity type: {}", def.entityType());
            return null;
        }
        return type.get().create(level, EntitySpawnReason.LOAD);
    }

    private static void applyState(Entity e, NpcData d) {
        e.setPos(d.x(), d.y(), d.z());
        e.xo = e.xOld = d.x();
        e.yo = e.yOld = d.y();
        e.zo = e.zOld = d.z();
        e.setYRot(d.yRot());
        e.setXRot(d.xRot());
        e.yRotO = d.yRot();
        e.xRotO = d.xRot();
        e.setNoGravity(true);

        if (e instanceof Mob mob) {
            mob.setNoAi(true);
        }
        if (e instanceof LivingEntity living) {
            living.yHeadRot = d.headYRot();
            living.yHeadRotO = d.headYRot();
            living.yBodyRot = d.bodyYRot();
            living.yBodyRotO = d.bodyYRot();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = d.equipment().get(slot.getName());
                if (stack != null && !stack.isEmpty()) {
                    living.setItemSlot(slot, stack.copy());
                }
            }
        }
    }

    public static String typeId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }
}
