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
import net.kztmc.mc.cinema.entity.EntityProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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

/**
 * NPC定義(NpcData)を保持し、クライアントのレベルに偽エンティティとして出し入れする。
 * 定義が本体、エンティティは使い捨て。チャンクのアンロード/ディメンション移動で消えても
 * 条件が揃えば tick で再スポーンする。
 */
public final class NpcManager {
    private static final List<NpcData> DEFS = new ArrayList<>();
    private static final Map<UUID, Entity> LIVE = new HashMap<>();

    private static ClientLevel liveLevel;
    private static HolderLookup.Provider registries;
    private static String worldKey;
    private static boolean dirty;
    private static int saveCooldown;
    private static UUID selected;
    /** サーバー側エンティティIDと衝突しないよう高い値から払い出す */
    private static int nextEntityId = 0x40000000;

    private NpcManager() {}

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            registries = handler.registryAccess();
            worldKey = NpcStorage.worldKey(client);
            DEFS.clear();
            DEFS.addAll(NpcStorage.load(worldKey, registries));
            LIVE.clear();
            SceneFreeze.clearRegistry();
            liveLevel = null;
            selected = null;
            Cinema.LOGGER.info("Loaded {} NPC(s) for {}", DEFS.size(), worldKey);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            saveNow();
            LIVE.clear();
            SceneFreeze.clearRegistry();
            DEFS.clear();
            liveLevel = null;
            worldKey = null;
            selected = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(NpcManager::tick);
    }

    // ---- public API ----

    public static List<NpcData> defs() {
        return List.copyOf(DEFS);
    }

    public static Optional<NpcData> get(UUID id) {
        return DEFS.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public static UUID selectedId() {
        return selected;
    }

    public static Optional<NpcData> selectedData() {
        return selected == null ? Optional.empty() : get(selected);
    }

    public static void select(UUID id) {
        selected = id;
    }

    public static void add(NpcData data) {
        DEFS.add(data);
        selected = data.id();
        dirty = true;
    }

    /** プレイヤーの足元・向かい合う向きにNPCを追加する */
    public static NpcData spawnAtPlayer(String kind, String entityType, String skin) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;
        float faceYaw = p.getYRot() + 180f; // プレイヤーの方を向かせる
        NpcData data = NpcData.create(kind, entityType, skin,
                mc.level.dimension().identifier().toString(),
                p.getX(), p.getY(), p.getZ(), faceYaw);
        add(data);
        return data;
    }

    /** 定義を差し替え、生きているエンティティにも即反映する(スライダー操作など) */
    public static void replace(NpcData data) {
        for (int i = 0; i < DEFS.size(); i++) {
            if (DEFS.get(i).id().equals(data.id())) {
                DEFS.set(i, data);
                Entity e = LIVE.get(data.id());
                if (e != null && !e.isRemoved()) applyState(e, data);
                dirty = true;
                return;
            }
        }
    }

    /** エンティティ自体を作り直す(種類やスキンを変えたとき用) */
    public static void respawn(UUID id) {
        despawn(id);
    }

    public static void remove(UUID id) {
        DEFS.removeIf(d -> d.id().equals(id));
        despawn(id);
        if (id.equals(selected)) selected = null;
        dirty = true;
    }

    public static void clear() {
        for (NpcData d : List.copyOf(DEFS)) despawn(d.id());
        DEFS.clear();
        selected = null;
        dirty = true;
    }

    // ---- tick ----

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || worldKey == null) return;

        if (level != liveLevel) { // ディメンション移動/再接続でレベルが別物になった
            LIVE.clear();
            liveLevel = level;
            SceneFreeze.clearRegistry();
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
        nextEntityId = EntityIds.assign(e, nextEntityId);
        SceneFreeze.register(e);   // 先に登録する(時刻の補間値の保存先)
        EntityTweaks.prepare(e);   // 牙など、そのままだと見えないものの対応
        applyState(e, def);        // 時刻などの保存値は、これで上書きされる
        level.addEntity(e);
        LIVE.put(def.id(), e);
    }

    private static void despawn(UUID id) {
        Entity e = LIVE.remove(id);
        if (e != null) SceneFreeze.unregister(e);
        if (e != null && liveLevel != null) {
            liveLevel.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static Entity createEntity(ClientLevel level, NpcData def) {
        if (NpcData.PLAYER.equals(def.kind())) {
            String name = def.skin().orElse("");
            GameProfile profile = new GameProfile(def.id(), name.isBlank() ? "NPC" : name);
            return new NpcPlayer(level, profile, SkinResolver.forName(name));
        }
        Identifier typeId = Identifier.tryParse(def.entityType());
        Optional<EntityType<?>> type = typeId == null
                ? Optional.empty()
                : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
        if (type.isEmpty()) {
            Cinema.LOGGER.warn("Unknown entity type: {}", def.entityType());
            return null;
        }
        return type.get().create(level, EntitySpawnReason.LOAD);
    }

    /** 何度呼んでも同じ結果になる(冪等)ようにしてある。生成時と編集時の両方で使う。 */
    private static void applyState(Entity e, NpcData d) {
        EntityProfiles.of(e).applyOptions(e, d.options());
        e.setPos(d.x(), d.y(), d.z());
        for (Entity part : EntityIds.subEntities(e)) {
            part.setPos(e.getX(), e.getY(), e.getZ());
            part.setOldPosAndRot();
        }
        e.xo = e.xOld = e.getX();
        e.yo = e.yOld = e.getY();
        e.zo = e.zOld = e.getZ();
        e.setYRot(d.yRot());
        e.setXRot(d.xRot());
        e.yRotO = d.yRot();
        e.xRotO = d.xRot();
        e.setNoGravity(true);

        // ポーズ: Player は tick で姿勢が再計算されるので、フラグ側も合わせる
        NpcPose pose = d.npcPose();
        e.setShiftKeyDown(pose == NpcPose.CROUCHING);
        e.setSwimming(pose == NpcPose.SWIMMING);
        e.setPose(pose.pose);

        if (e instanceof NpcPlayer player) {
            player.setModelName(d.model().orElse(null));
        }
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
                living.setItemSlot(slot, stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        EntityProfiles.of(e).applyTransform(e);   // 位置に合わせて動かす部品(ドラゴンの当たり判定)の配置
        EntityTweaks.applyAnim(e, d.animTickOrDefault());
    }
}
