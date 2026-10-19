package net.kztmc.mc.cinema.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/**
 * NPC 1体ぶんの「定義」。ワールド再入場時はこれを元にエンティティを作り直す。
 * 見た目に関する設定は NpcStyle にまとめてある(group の上限16フィールドを避けるため)。
 * 項目を増やすときは、NpcStyle に optionalFieldOf で足すと、旧ファイルも読める。
 */
public record NpcData(
        UUID id,
        String kind,              // "player" | "mob"
        String entityType,        // mob のとき "minecraft:zombie" など。player は "minecraft:player"
        Optional<String> skin,    // player のスキンのユーザー名
        String dimension,
        double x, double y, double z,
        float yRot, float xRot, float headYRot, float bodyYRot,
        Map<String, ItemStack> equipment, // EquipmentSlot#getName() -> ItemStack
        NpcStyle style
) {
    public static final String PLAYER = "player";
    public static final String MOB = "mob";

    public static final Codec<NpcData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(NpcData::id),
            Codec.STRING.fieldOf("kind").forGetter(NpcData::kind),
            Codec.STRING.fieldOf("entity_type").forGetter(NpcData::entityType),
            Codec.STRING.optionalFieldOf("skin").forGetter(NpcData::skin),
            Codec.STRING.fieldOf("dimension").forGetter(NpcData::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(NpcData::x),
            Codec.DOUBLE.fieldOf("y").forGetter(NpcData::y),
            Codec.DOUBLE.fieldOf("z").forGetter(NpcData::z),
            Codec.FLOAT.fieldOf("y_rot").forGetter(NpcData::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(NpcData::xRot),
            Codec.FLOAT.fieldOf("head_y_rot").forGetter(NpcData::headYRot),
            Codec.FLOAT.fieldOf("body_y_rot").forGetter(NpcData::bodyYRot),
            Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC)
                    .optionalFieldOf("equipment", Map.of()).forGetter(NpcData::equipment),
            NpcStyle.CODEC.forGetter(NpcData::style)
    ).apply(i, NpcData::new));

    public static NpcData create(String kind, String entityType, String skin,
                                 String dimension, double x, double y, double z, float yaw) {
        return new NpcData(UUID.randomUUID(), kind, entityType, Optional.ofNullable(skin), dimension,
                x, y, z, yaw, 0f, yaw, yaw, Map.of(), NpcStyle.EMPTY);
    }

    // ---- NpcStyle への窓口(既存の呼び出しがそのまま使える) ----

    public Optional<String> model() {
        return style.model();
    }

    public Optional<String> pose() {
        return style.pose();
    }

    public Optional<Float> animTick() {
        return style.animTick();
    }

    public Map<String, Integer> options() {
        return style.options();
    }

    public NpcPose npcPose() {
        return NpcPose.of(style.pose().orElse(null));
    }

    /** 保存値が無ければ、種類ごとの初期値 */
    public float animTickOrDefault() {
        return style.animTick().orElse((float) EntityTweaks.defaultAnimTick(entityType));
    }

    /** エンティティ固有の値。保存値が無ければ def */
    public int option(String key, int def) {
        return style.options().getOrDefault(key, def);
    }

    // ---- 更新 ----

    private NpcData withStyle(NpcStyle s) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot,
                equipment, s);
    }

    public NpcData withTransform(double x, double y, double z, float yRot, float xRot, float headYRot, float bodyYRot) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot,
                equipment, style);
    }

    public NpcData withEquipment(Map<String, ItemStack> eq) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot,
                Map.copyOf(eq), style);
    }

    public NpcData withPose(NpcPose p) {
        return withStyle(style.withPose(p.name()));
    }

    public NpcData withModel(Optional<String> m) {
        return withStyle(style.withModel(m));
    }

    public NpcData withAnimTick(float tick) {
        return withStyle(style.withAnimTick(Math.round(tick * 10f) / 10f));
    }

    public NpcData withOption(String key, int value) {
        return withStyle(style.withOption(key, value));
    }
}