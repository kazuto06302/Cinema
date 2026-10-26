package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.profile.animation.AnimationStates;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * アルマジロ。状態(ArmadilloState)と、丸まる・開く・覗くのアニメーションで決まる。
 *   丸まる : SCARED(体が殻に隠れた状態)+ 丸まるアニメーション
 *   開く   : UNROLLING。開き始めの5tickまでは殻に隠れ、以降は体が見える(状態の経過tickを、時間に合わせる)
 *   覗く   : SCARED + 覗くアニメーション
 */
public final class ArmadilloProfile extends EntityProfile {
    private static final String[] STATES = {"IDLE", "SCARED", "UNROLLING", "SCARED"};
    private static final String[] ANIMS = {null, "rollUpAnimationState", "rollOutAnimationState", "peekAnimationState"};

    public ArmadilloProfile() {
        super("minecraft:armadillo");
        choice("state", "状態", 0, "通常", "丸まる", "開く", "覗く");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = Mth.clamp(options.getOrDefault("state", 0), 0, STATES.length - 1);
        int n = (int) Math.floor(time);

        EntityReflect.invokeWithEnumArg(e, "switchToState", STATES[mode]);
        EntityReflect.setLongQuiet(e, "inStateTicks", mode == 2 ? n : 100);

        for (int i = 1; i < ANIMS.length; i++) {
            if (i == mode) {
                AnimationStates.play(e, ANIMS[i]);
            } else {
                AnimationStates.stop(e, ANIMS[i]);
            }
        }
    }
}