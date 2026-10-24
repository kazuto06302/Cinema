package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** キツネ。姿勢(立つ・座る・寝る・しゃがむ・飛びかかり・顔を突っ込む)と色(赤・雪)。 */
public final class FoxProfile extends EntityProfile {
    private static final String[] COLORS = {"RED", "SNOW"};

    public FoxProfile() {
        super("minecraft:fox");
        choice("state", "姿勢", 0, "立つ", "座る", "寝る", "しゃがむ", "飛びかかり", "顔を突っ込む");
        choice("color", "色", 0, "赤", "雪");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int s = options.getOrDefault("state", 0);
        EntityReflect.invokeFirst(e, List.of("setSitting"), s == 1);
        EntityReflect.invokeFirst(e, List.of("setSleeping"), s == 2);
        EntityReflect.invokeFirst(e, List.of("setIsCrouching"), s == 3 || s == 4);
        EntityReflect.invokeFirst(e, List.of("setIsPouncing"), s == 4);
        EntityReflect.invokeFirst(e, List.of("setFaceplanted"), s == 5);
        // しゃがみ・飛びかかりの体の傾き(0〜1と、1つ前の値)
        float crouch = (s == 3 || s == 4) ? 1f : 0f;
        EntityReflect.setFloat(e, "crouchAmount", crouch);
        EntityReflect.setFloat(e, "crouchAmountO", crouch);

        int c = Math.max(0, Math.min(COLORS.length - 1, options.getOrDefault("color", 0)));
        EntityReflect.invokeWithEnumArg(e, "setVariant", COLORS[c]);
    }
}