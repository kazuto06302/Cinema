package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * パンダ。立つ・座る・仰向け・転がる・くしゃみ・食べる。
 * 姿勢は、フラグと、その移行量(sitAmount など。0〜1と、1つ前の値)で決まるので、両方を書き込む。
 */
public final class PandaProfile extends EntityProfile {
    public PandaProfile() {
        super("minecraft:panda");
        choice("pose", "ポーズ", 0, "立つ", "座る", "仰向け", "転がる", "くしゃみ", "食べる");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int pose = options.getOrDefault("pose", 0);
        flag(e, List.of("sit", "setSitting"), "sitAmount", pose == 1);
        flag(e, List.of("setOnBack"), "onBackAmount", pose == 2);
        flag(e, List.of("roll", "setRolling"), "rollAmount", pose == 3);
        flag(e, List.of("sneeze", "setSneezing"), null, pose == 4);
        flag(e, List.of("eat", "setEating"), null, pose == 5);
        // くしゃみ・食事の、顔の動きの進み具合
        EntityReflect.setInt(e, "sneezeCounter", pose == 4 ? 10 : 0);
        EntityReflect.setInt(e, "eatCounter", pose == 5 ? 10 : 0);
    }

    private static void flag(Entity e, List<String> methods, String amountField, boolean on) {
        EntityReflect.invokeFirst(e, methods, on);
        if (amountField != null) {
            float v = on ? 1f : 0f;
            EntityReflect.setFloat(e, amountField, v);
            EntityReflect.setFloat(e, amountField + "O", v);
        }
    }
}