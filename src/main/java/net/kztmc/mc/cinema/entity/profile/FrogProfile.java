package net.kztmc.mc.cinema.entity.profile;

/** カエル。立つ・歩く・ジャンプ・鳴く・舌を出す・泳ぐ。 */
public final class FrogProfile extends AnimationModeProfile {
    public FrogProfile() {
        super("state", "動作",
                new String[] {"立つ", "歩く", "ジャンプ", "鳴く", "舌を出す", "泳ぐ"},
                new String[] {null, "walkAnimationState", "jumpAnimationState", "croakAnimationState",
                        "tongueAnimationState", "swimAnimationState"},
                "minecraft:frog");
    }
}