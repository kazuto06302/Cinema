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
        String dimension,
        double x, double y, double z,
        float yRot, float xRot, float headYRot, float bodyYRot,
        Optional<String> pose,    // NpcPose の名前
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
            Codec.STRING.optionalFieldOf("pose").forGetter(NpcData::pose),
            Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC)
                    .optionalFieldOf("equipment", Map.of()).forGetter(NpcData::equipment)
    ).apply(i, NpcData::new));

    public static NpcData create(String kind, String entityType, String skin,
                                 String dimension, double x, double y, double z, float yaw) {
        return new NpcData(UUID.randomUUID(), kind, entityType, Optional.ofNullable(skin),
                dimension, x, y, z, yaw, 0f, yaw, yaw, Optional.empty(), Map.of());
    }

    public NpcPose npcPose() {
        return NpcPose.of(pose.orElse(null));
    }

    public NpcData withTransform(double x, double y, double z, float yRot, float xRot, float headYRot, float bodyYRot) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot, pose, equipment);
    }

    public NpcData withPose(NpcPose p) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot,
                Optional.of(p.name()), equipment);
    }

    public NpcData withEquipment(Map<String, ItemStack> eq) {
        return new NpcData(id, kind, entityType, skin, dimension, x, y, z, yRot, xRot, headYRot, bodyYRot, pose, Map.copyOf(eq));
    }
}
