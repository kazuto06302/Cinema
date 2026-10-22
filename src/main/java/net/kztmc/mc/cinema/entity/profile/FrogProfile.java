package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.RegistryHolders;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;

/** カエル。立つ・歩く・ジャンプ・鳴く・舌を出す・泳ぐ。色は、温帯(オレンジ)・温暖(白)・寒冷(緑)。 */
public final class FrogProfile extends AnimationModeProfile {
    private static final String[] COLORS = {"temperate", "warm", "cold"};

    public FrogProfile() {
        super("state", "動作",
                new String[] {"立つ", "歩く", "ジャンプ", "鳴く", "舌を出す", "泳ぐ"},
                new String[] {null, "walkAnimationState", "jumpAnimationState", "croakAnimationState",
                        "tongueAnimationState", "swimAnimationState"},
                "minecraft:frog");
        choice("color", "色", 0, "温帯(オレンジ)", "温暖(白)", "寒冷(緑)");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        super.applyAnim(e, time, options);
        int color = Math.max(0, Math.min(COLORS.length - 1, options.getOrDefault("color", 0)));
        Holder<?> variant = RegistryHolders.get(e, Registries.FROG_VARIANT, COLORS[color]);
        if (variant != null) {
            EntityReflect.invokeFirst(e, List.of("setVariant"), variant);
        }
    }
}