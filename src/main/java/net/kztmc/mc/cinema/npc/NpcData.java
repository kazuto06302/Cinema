package net.kztmc.mc.cinema.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

public record NpcData(
        UUID id,
        String kind,              // "player" | "mob"
        String entityType,        // mob のとき "minecraft:zombie" など。player は "minecraft:player"
        Optional<String> skin,    // player のスキン/表示名
        String dimension,         // "minecraft:overworld" など
        double x, double y, double z,
        float yRot, float xRot, float headYRot, float bodyYRot,
        Map<String, ItemStack> equipment // EquipmentSlot#getName() -> ItemStack
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
                    .optionalFieldOf("equipment", Map.of()).forGetter(NpcData::equipment)
    ).apply(i, NpcData::new));

    public NpcData withTransform(double x, double y, double z, float yRot, float xRot, float headYRot, float bodyYRot) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot, equipment);
    }

    public NpcData withEquipment(Map<String, ItemStack> equipment) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot, Map.copyOf(equipment));
    }
}
