package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * キツネ。姿勢(立つ・座る・寝る・しゃがむ・飛びかかり・顔を突っ込む)、首をかしげる、色(赤・雪)。
 * 飛びかかりと顔を突っ込むは、体の上下の向きを自動で付ける(頭の上下スライダーは、この2つの姿勢では上書きされる)。
 */
public final class FoxProfile extends EntityProfile {
    private static final String[] COLORS = {"RED", "SNOW"};

    public FoxProfile() {
        super("minecraft:fox");
        choice("state", "姿勢", 0, "立つ", "座る", "寝る", "しゃがむ", "飛びかかり", "顔を突っ込む");
        toggle("interested", "首をかしげる", false);
        choice("color", "色", 0, "赤", "雪");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        // 一度、全部の状態を解除してから、選んだものだけを立てる
        EntityReflect.invokeFirst(e, List.of("clearStates"));

        int s = options.getOrDefault("state", 0);
        if (s == 1) EntityReflect.invokeFirst(e, List.of("setSitting"), true);
        if (s == 2) EntityReflect.invokeFirst(e, List.of("setSleeping"), true);
        if (s == 3 || s == 4) EntityReflect.invokeFirst(e, List.of("setIsCrouching"), true);
        if (s == 4) EntityReflect.invokeFirst(e, List.of("setIsPouncing"), true);
        if (s == 5) EntityReflect.invokeFirst(e, List.of("setFaceplanted"), true);

        // しゃがみ・飛びかかりの体の傾き(0〜1と、1つ前の値)
        float crouch = (s == 3 || s == 4) ? 1f : 0f;
        EntityReflect.setFloat(e, "crouchAmount", crouch);
        EntityReflect.setFloat(e, "crouchAmountO", crouch);

        // 飛びかかりは斜め上、顔を突っ込むは下向きに、体を傾ける
        if (s == 4 || s == 5) {
            float pitch = s == 4 ? -35f : 60f;
            e.setXRot(pitch);
            e.xRotO = pitch;
        }

        // 首をかしげる(頭の傾き)
        boolean interested = options.getOrDefault("interested", 0) == 1;
        EntityReflect.invokeFirst(e, List.of("setIsInterested"), interested);
        float tilt = interested ? 1f : 0f;
        EntityReflect.setFloat(e, "interestedAngle", tilt);
        EntityReflect.setFloat(e, "interestedAngleO", tilt);

        int c = Math.max(0, Math.min(COLORS.length - 1, options.getOrDefault("color", 0)));
        EntityReflect.invokeWithEnumArg(e, "setVariant", COLORS[c]);
    }
}