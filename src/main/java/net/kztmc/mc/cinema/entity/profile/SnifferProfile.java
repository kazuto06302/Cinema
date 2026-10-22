package net.kztmc.mc.cinema.entity.profile;

/** スニッファー。待機・喜ぶ・匂いを探る・嗅ぐ・掘る・出てくる。 */
public final class SnifferProfile extends AnimationModeProfile {
    public SnifferProfile() {
        super("state", "動作",
                new String[] {"待機", "喜ぶ", "匂いを探る", "嗅ぐ", "掘る", "出てくる"},
                new String[] {null, "feelingHappyAnimationState", "scentingAnimationState",
                        "sniffingAnimationState", "diggingAnimationState", "risingAnimationState"},
                "minecraft:sniffer");
        // 歩行は、キーフレームのアニメーション。1周期は約2秒、animateWalk の係数は9.0(どちらも推測値)
        walkKeyframe(2.0f, 9.0f);
    }
}