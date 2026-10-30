package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.RegistryHolders;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;

/**
 * カエル。立つ・ジャンプ・鳴く・舌を出す・水中で待つ。色は、温帯(オレンジ)・温暖(白)・寒冷(緑)。
 * 歩く動作は、フィールドのアニメーションではなく、手足の動き(キーフレームの歩行)なので、歩行スライダーで動かす。
 */
public final class FrogProfile extends AnimationModeProfile {
    private static final String[] COLORS = {"temperate", "warm", "cold"};

    public FrogProfile() {
        super("state", "動作",
                new String[] {"立つ", "ジャンプ", "鳴く", "舌を出す", "水中で待つ"},
                new String[] {null, "jumpAnimationState", "croakAnimationState", "tongueAnimationState",
                        "swimIdleAnimationState"},
                "minecraft:frog");
        dropdownChoice("color", "色", 0, "温帯(オレンジ)", "温暖(白)", "寒冷(緑)");
        // 歩行はキーフレーム。1周期は約1.25秒、animateWalk の係数は1.5(どちらも推測値)
        walkKeyframe(1.25f, 1.5f);
    }

    @Override
    protected void onMode(Entity e, int mode, float time) {
        EntityReflect.setBooleanFirst(e, mode == 4, "wasTouchingWater", "touchingWater", "inWater");
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