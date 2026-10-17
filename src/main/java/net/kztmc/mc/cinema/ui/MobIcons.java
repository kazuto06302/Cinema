package net.kztmc.mc.cinema.ui;

import java.nio.charset.StandardCharsets;
import java.util.*;

import com.mojang.authlib.GameProfile;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.npc.EntityTweaks;
import net.kztmc.mc.cinema.npc.NpcPlayer;
import net.kztmc.mc.cinema.npc.SkinResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** モブ/エンティティのアイコン描画。全身を描く → 描けないものは色つきタイル+頭文字。 */
public final class MobIcons {
    private MobIcons() {}

    /** false にすると全部タイル表示(エンティティ描画で問題が出たときの退避用) */
    public static final boolean RENDER_ENTITIES = true;

    /** 枠いっぱいを1.0としたときの基本の大きさ(全モブ共通)。調整パネルから動かせる */
    private static float fitGlobal = 1.00f;

    /** 切り取り範囲をアイコンの外側にどれだけ広げるか(アイコンサイズに対する割合) */
    private static final float CLIP_MARGIN = 1f / 6f;

    /** 縦回転の符号。見下ろす/見上げるが逆に見えたら反転する */
    private static final float PITCH_SIGN = -1f;
    /** Z回転(roll)の符号。傾く向きが逆に見えたら -1f にする */
    private static final float ROLL_SIGN = 1f;
    /** 高さの符号。+で下に動いたら -1f と +1f を入れ替える */
    private static final float HEIGHT_SIGN = -1f;

    private static final String PLAYER_PREFIX = "player:";

    /** プレイヤーのカード用のID("player:名前")。paint にそのまま渡せる */
    public static String playerId(String name) {
        return PLAYER_PREFIX + name;
    }

    /** プレイヤー(スキン)のカードを描く */
    public static void paintPlayer(GuiGraphicsExtractor g, Font font, String name, int x, int y, int size) {
        paint(g, font, playerId(name), name.isBlank() ? "NPC" : name, x, y, size);
    }

    /**
     * モブごとの表示スタイル。
     *   fit   : 大きさの倍率(1.0が標準、小さいほど小さく描く)
     *   yaw   : 横回転(度)。0=正面、90=真横。プラスで画面の左向き
     *   pitch : 縦回転(度)。上下の傾き。符号が逆なら PITCH_SIGN を反転
     *   roll  : Z軸回転(度)。画面の中心を軸にアイコン全体を傾ける。符号が逆なら ROLL_SIGN を反転
     *   height: 高さ(%)。アイコン枠の高さに対する割合で上下にずらす。+で上。逆なら HEIGHT_SIGN を反転
     */
    public record IconStyle(float fit, float yaw, float pitch, float roll, float height) {
        public static final IconStyle DEFAULT = new IconStyle(1f, 20f, 0f, 0f, 0f);

        /** 従来の3項目だけの指定。roll と height は 0 */
        public static IconStyle of(float fit, float yaw, float pitch) {
            return new IconStyle(fit, yaw, pitch, 0f, 0f);
        }

        public static IconStyle of(float fit, float yaw, float pitch, float roll, float height) {
            return new IconStyle(fit, yaw, pitch, roll, height);
        }

        /** 大きさだけ変えて、向きは標準のまま */
        public static IconStyle fit(float fit) {
            return new IconStyle(fit, DEFAULT.yaw, DEFAULT.pitch, 0f, 0f);
        }

        public IconStyle withFit(float v) {
            return new IconStyle(v, yaw, pitch, roll, height);
        }

        public IconStyle withYaw(float v) {
            return new IconStyle(fit, v, pitch, roll, height);
        }

        public IconStyle withPitch(float v) {
            return new IconStyle(fit, yaw, v, roll, height);
        }

        public IconStyle withRoll(float v) {
            return new IconStyle(fit, yaw, pitch, v, height);
        }

        public IconStyle withHeight(float v) {
            return new IconStyle(fit, yaw, pitch, roll, v);
        }
    }

    /** IDを個別に指定するスタイル(最優先)。足りないモブはここに追加する */
    private static final Map<String, IconStyle> STYLES = Map.ofEntries(
            Map.entry("minecraft:acacia_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:acacia_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:allay", IconStyle.of(0.70f, 20f, 0f)),
            Map.entry("minecraft:armadillo", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:arrow", IconStyle.of(0.50f, 80f, 0f, 150f, -40f)),
            Map.entry("minecraft:axolotl", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:bamboo_raft", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:bamboo_chest_raft", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:bat", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:bee", IconStyle.of(0.60f, 20f, -5f, 5f, -5f)),
            Map.entry("minecraft:birch_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:birch_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:camel", IconStyle.of(0.85f, 20f, 0f)),
            Map.entry("minecraft:camel_husk", IconStyle.of(0.85f, 20f, 0f)),
            Map.entry("minecraft:cat", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:cave_spider", IconStyle.of(0.70f, 20f, 0f)),
            Map.entry("minecraft:cherry_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:cherry_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:chicken", IconStyle.of(0.65f, 20f, 0f)),
            Map.entry("minecraft:cod", IconStyle.of(0.75f, 90f, -75f, 0f, 15f)),
            Map.entry("minecraft:copper_golem", IconStyle.of(0.60f, 20f, 0f)),
            Map.entry("minecraft:cow", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:creaking", IconStyle.of(1.20f, 20f, 0f)),
            Map.entry("minecraft:cushion", IconStyle.of(1.00f, 0f, 30f)),
            Map.entry("minecraft:dark_oak_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:dark_oak_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:dolphin", IconStyle.of(0.80f, 30f, 15f)),
            Map.entry("minecraft:donkey", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:dragon_fireball", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:elder_guardian", IconStyle.of(0.70f, 30f, 0f)),
            Map.entry("minecraft:ender_dragon", IconStyle.of(2.00f, 0f, -15f, 0f, 15f)),
            Map.entry("minecraft:endermite", IconStyle.of(0.50f, 20f, 15f)),
            Map.entry("minecraft:eye_of_ender", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:falling_block", IconStyle.of(0.70f, 0f, 15f)),
            Map.entry("minecraft:fireball", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:firework_rocket", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:fox", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:frog", IconStyle.of(0.60f, 20f, 0f)),
            Map.entry("minecraft:ghast", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:glow_item_frame", IconStyle.of(0.50f, 20f, 0f, 0f, 30f)),
            Map.entry("minecraft:glow_squid", IconStyle.of(0.60f, 20f, 0f)),
            Map.entry("minecraft:goat", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:guardian", IconStyle.of(0.70f, 30f, 0f)),
            Map.entry("minecraft:happy_ghast", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:hoglin", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:horse", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:iron_golem", IconStyle.of(1.30f, 20f, 0f)),
            Map.entry("minecraft:item_frame", IconStyle.of(0.50f, 20f, 0f, 0f, 30f)),
            Map.entry("minecraft:jungle_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:jungle_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:leash_knot", IconStyle.of(0.60f, 20f, 15f)),
            Map.entry("minecraft:lingering_potion", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:llama", IconStyle.of(0.90f, 20f, 0f)),
            Map.entry("minecraft:llama_spit", IconStyle.of(0.40f, 20f, 0f)),
            Map.entry("minecraft:magma_cube", IconStyle.of(0.75f, 20f, 15f)),
            Map.entry("minecraft:mangrove_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:mangrove_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:chest_minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:command_block_minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:furnace_minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:hopper_minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:spawner_minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:tnt_minecart", IconStyle.of(0.80f, 45f, 25f)),
            Map.entry("minecraft:mooshroom", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:mule", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:nautilus", IconStyle.of(0.70f, 20f, 0f)),
            Map.entry("minecraft:oak_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:oak_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:ocelot", IconStyle.of(0.70f, 20f, 0f)),
            Map.entry("minecraft:painting", IconStyle.of(0.40f, 20f, 180f, 0f, 15f)),
            Map.entry("minecraft:pale_oak_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:pale_oak_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:panda", IconStyle.of(0.85f, 20f, 0f)),
            Map.entry("minecraft:phantom", IconStyle.of(0.70f, 20f, 0f)),
            Map.entry("minecraft:pig", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:poplar_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:poplar_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:tnt", IconStyle.of(0.70f, 0f, 15f)),
            Map.entry("minecraft:pufferfish", IconStyle.of(0.85f, 20f, 15f)),
            Map.entry("minecraft:rabbit", IconStyle.of(0.50f, 20f, 30f, -10f, -5f)),
            Map.entry("minecraft:ravager", IconStyle.of(0.85f, 20f, 0f)),
            Map.entry("minecraft:salmon", IconStyle.of(0.75f, 90f, -75f, 0f, 15f)),
            Map.entry("minecraft:sheep", IconStyle.of(0.85f, 20f, 0f)),
            Map.entry("minecraft:shulker", IconStyle.of(0.70f, 20f, 0f)),
            Map.entry("minecraft:shulker_bullet", IconStyle.of(0.40f, 20f, 0f)),
            Map.entry("minecraft:silverfish", IconStyle.of(0.50f, 20f, 15f)),
            Map.entry("minecraft:skeleton_horse", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:slime", IconStyle.of(0.85f, 20f, 0f)),
            Map.entry("minecraft:small_fireball", IconStyle.of(0.60f, 20f, 0f, 0f, 15f)),
            Map.entry("minecraft:sniffer", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:snowball", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:spectral_arrow", IconStyle.of(0.50f, 80f, 0f, 150f, -40f)),
            Map.entry("minecraft:spider", IconStyle.of(0.90f, 20f, 0f)),
            Map.entry("minecraft:splash_potion", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:spruce_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:spruce_chest_boat", IconStyle.of(0.75f, 45f, 25f)),
            Map.entry("minecraft:squid", IconStyle.of(0.60f, 20f, 0f)),
            Map.entry("minecraft:sulfur_cube", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:tadpole", IconStyle.of(0.85f, 20f, 15f)),
            Map.entry("minecraft:experience_bottle", IconStyle.of(0.45f, 20f, 0f)),
            Map.entry("minecraft:egg", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:ender_pearl", IconStyle.of(0.50f, 20f, 0f)),
            Map.entry("minecraft:evoker_fangs", IconStyle.of(0.50f, -70f, 0f)),
            Map.entry("minecraft:trader_llama", IconStyle.of(0.90f, 20f, 0f)),
            Map.entry("minecraft:trident", IconStyle.of(0.75f, 60f, 10f, 140f, -50f)),
            Map.entry("minecraft:tropical_fish", IconStyle.of(0.75f, 90f, -75f, 0f, 30f)),
            Map.entry("minecraft:turtle", IconStyle.of(0.85f, 20f, 15f)),
            Map.entry("minecraft:vex", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:warden", IconStyle.of(0.90f, 20f, 0f)),
            Map.entry("minecraft:breeze_wind_charge", IconStyle.of(0.50f, 20f, -15f, 0f, 35f)),
            Map.entry("minecraft:wind_charge", IconStyle.of(0.50f, 20f, -15f, 0f, 35f)),
            Map.entry("minecraft:wither_skull", IconStyle.of(0.30f, -160f, 15f)),
            Map.entry("minecraft:wolf", IconStyle.of(0.75f, 20f, 0f)),
            Map.entry("minecraft:zoglin", IconStyle.of(0.80f, 20f, 0f)),
            Map.entry("minecraft:zombie_horse", IconStyle.of(0.80f, 20f, 0f, 0f, -10f)),
            Map.entry("minecraft:zombie_nautilus", IconStyle.of(0.70f, 20f, 0f))
    );

    /** IDの末尾で指定するスタイル。STYLES に無いときだけ、上から順に判定する */
    private static final List<Map.Entry<String, IconStyle>> SUFFIX_STYLES = List.of();

    /** 調整パネルで一時的に上書きしているスタイル(最優先。ファイルには保存されない) */
    private static final Map<String, IconStyle> DEBUG_STYLES = new HashMap<>();

    /** 実際に使われるスタイル(調整中の上書き → 個別指定 → 末尾指定 → 標準) */
    public static IconStyle styleOf(String id) {
        IconStyle d = DEBUG_STYLES.get(id);
        if (d != null) return d;
        IconStyle s = STYLES.get(id);
        if (s != null) return s;
        for (Map.Entry<String, IconStyle> en : SUFFIX_STYLES) {
            if (id.endsWith(en.getKey())) return en.getValue();
        }
        return IconStyle.DEFAULT;
    }

    // ---- 調整パネル用 ----

    public static float getGlobalFit() {
        return fitGlobal;
    }

    public static void setGlobalFit(float v) {
        fitGlobal = v;
    }

    public static void setDebugStyle(String id, IconStyle style) {
        DEBUG_STYLES.put(id, style);
    }

    public static void clearDebugStyle(String id) {
        DEBUG_STYLES.remove(id);
    }

    /** STYLES にそのまま貼れる1行 */
    /** STYLES にそのまま貼れる1行 */
    public static String styleLine(String id) {
        IconStyle s = styleOf(id);
        if (s.roll() == 0f && s.height() == 0f) {
            return String.format(Locale.ROOT, "Map.entry(\"%s\", IconStyle.of(%.2ff, %.0ff, %.0ff)),",
                    id, s.fit(), s.yaw(), s.pitch());
        }
        return String.format(Locale.ROOT, "Map.entry(\"%s\", IconStyle.of(%.2ff, %.0ff, %.0ff, %.0ff, %.0ff)),",
                id, s.fit(), s.yaw(), s.pitch(), s.roll(), s.height());
    }

    /**
     * 描画しない(タイル表示にする)エンティティ。
     * 何も映らない・描画に必要な情報が無くて落ちる可能性があるもの。
     * 落ちるモブが見つかったら、ここにIDを足す。
     */
    private static final Set<String> NO_RENDER = Set.of(
            "minecraft:marker",
            "minecraft:interaction",
            "minecraft:area_effect_cloud",
            "minecraft:block_display",
            "minecraft:item_display",
            "minecraft:text_display",
            "minecraft:item",
            "minecraft:lightning_bolt",
            "minecraft:ominous_item_spawner"
    );

    private static final Map<String, Optional<Entity>> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static Level cachedLevel;

    /** アイコン用エンティティのID。NPC(0x40000000〜)やサーバー側のIDと重ならない帯を使う */
    private static int nextIconId = 0x50000000;

    /** 表示用のエンティティ。レベルには追加せず、描画専用に1体だけ作って使い回す */
    private static Entity entityOf(String id) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (level != cachedLevel) { // ワールドが変わったら作り直す
            CACHE.clear();
            FAILED.clear();
            cachedLevel = level;
        }
        return CACHE.computeIfAbsent(id, k -> create(level, k)).orElse(null);
    }

    private static Optional<Entity> create(Level level, String id) {
        try {
            if (id.startsWith(PLAYER_PREFIX)) {
                if (!(level instanceof ClientLevel clientLevel)) return Optional.empty();
                String name = id.substring(PLAYER_PREFIX.length());
                UUID uuid = UUID.nameUUIDFromBytes(("cinema:" + name).getBytes(StandardCharsets.UTF_8));
                NpcPlayer p = new NpcPlayer(clientLevel,
                        new GameProfile(uuid, name.isBlank() ? "NPC" : name), SkinResolver.forName(name));
                p.setId(nextIconId++);
                return Optional.of(p);
            }
            Identifier typeId = Identifier.tryParse(id);
            if (typeId == null) return Optional.empty();
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
            if (type == null) return Optional.empty();
            Entity e = type.create(level, EntitySpawnReason.LOAD);
            if (e == null) return Optional.empty();
            e.setId(nextIconId++);   // 未割り当てのまま描画すると IllegalStateException になる
            EntityTweaks.prepare(e);
            return Optional.of(e);
        } catch (Throwable t) {
            Cinema.LOGGER.warn("Failed to create icon entity for {}", id, t);
            return Optional.empty();
        }
    }

    /** x, y から size 四方にアイコンを描く */
    public static void paint(GuiGraphicsExtractor g, Font font, String entityId, String name,
                             int x, int y, int size) {
        if (RENDER_ENTITIES && !NO_RENDER.contains(entityId) && !FAILED.contains(entityId)) {
            Entity e = entityOf(entityId);
            if (e != null && drawEntity(g, e, entityId, x, y, size)) return;
        }

        // 描けないもの: IDから色を決めたタイルに頭文字
        float hue = (Math.abs(entityId.hashCode()) % 360) / 360f;
        int color = 0xFF000000 | Mth.hsvToRgb(hue, 0.45f, 0.55f);
        Theme.roundRect(g, x, y, size, size, color);
        String initial = name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0)));
        Theme.textCentered(g, font, initial, x + size / 2, y + (size - Theme.textH()) / 2, 0xFFFFFFFF);
    }

    private static boolean drawEntity(GuiGraphicsExtractor g, Entity e, String id, int x, int y, int size) {
        try {
            IconStyle style = styleOf(id);
            float extent = Math.max(0.1f, Math.max(e.getBbHeight(), e.getBbWidth()));
            float scale = size * fitGlobal * style.fit() / extent;

            int margin = Math.round(size * CLIP_MARGIN);
            drawEntity(g, e, scale, style, size,
                    x - margin, y - margin, x + size + margin, y + size + margin);
            return true;
        } catch (Throwable t) {
            FAILED.add(id);
            Cinema.LOGGER.warn("Failed to draw icon entity for {}", id, t);
            return false;
        }
    }

    /** インベントリ画面でプレイヤーを描く処理と同じ手順を、あらゆるエンティティ向けに書いたもの */
    private static void drawEntity(GuiGraphicsExtractor g, Entity e, float scale, IconStyle style, int size,
                                   int x0, int y0, int x1, int y1) {
        float rollRad = ROLL_SIGN * (float) Math.toRadians(style.roll());

        // 縦回転(カメラの傾き)と、Z回転(画面内の傾き)
        Quaternionf rotX = new Quaternionf().rotateX(PITCH_SIGN * (float) Math.toRadians(style.pitch()));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI + rollRad).mul(rotX);

        // 向きを一時的に書き換えて描画状態を作り、終わったら元に戻す
        LivingEntity living = e instanceof LivingEntity l ? l : null;
        float oYRot = e.getYRot(), oXRot = e.getXRot(), oYRotO = e.yRotO, oXRotO = e.xRotO;
        float oBody = 0, oBodyO = 0, oHead = 0, oHeadO = 0;
        if (living != null) {
            oBody = living.yBodyRot;
            oBodyO = living.yBodyRotO;
            oHead = living.yHeadRot;
            oHeadO = living.yHeadRotO;
        }

        // 横回転: 180°で正面。体と頭を同じ向きにそろえる
        float facing = 180f + style.yaw();
        e.setYRot(facing);
        e.setXRot(0f);
        e.yRotO = facing;
        e.xRotO = 0f;
        if (living != null) {
            living.yBodyRot = facing;
            living.yBodyRotO = facing;
            living.yHeadRot = facing;
            living.yHeadRotO = facing;
        }

        try {
            float entityScale = living != null ? living.getScale() : 1f;
            float paramScale = scale / entityScale;

            // 従来の位置(roll=0, height=0 のときはこれと同じ)
            Vector3f translation = new Vector3f(0f, e.getBbHeight() / 2f + 0.0625f * entityScale, 0f);
            // Z回転は画面の中心を軸に回すので、位置のずらしも同じ角度だけ回す
            new Quaternionf().rotateZ(rollRad).transform(translation);
            // 高さ: アイコン枠に対する割合を、ブロック単位に直して足す
            translation.y += HEIGHT_SIGN * (style.height() / 100f * size) / paramScale;

            EntityRenderState state = createState(e);
            g.entity(state, paramScale, translation, rotation, rotX, x0, y0, x1, y1);
        } finally {
            e.setYRot(oYRot);
            e.setXRot(oXRot);
            e.yRotO = oYRotO;
            e.xRotO = oXRotO;
            if (living != null) {
                living.yBodyRot = oBody;
                living.yBodyRotO = oBodyO;
                living.yHeadRot = oHead;
                living.yHeadRotO = oHeadO;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityRenderState createState(Entity e) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(e);
        return renderer.createRenderState(e, 1.0f);
    }
}