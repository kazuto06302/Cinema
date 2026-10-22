package net.kztmc.mc.cinema.entity.profile;

/** スニッファー。待機・喜ぶ・匂いを探る・嗅ぐ・掘る・出てくる。 */
public final class SnifferProfile extends AnimationModeProfile {
    public SnifferProfile() {
        super("state", "動作",
                new String[] {"待機", "喜ぶ", "匂いを探る", "嗅ぐ", "掘る", "出てくる"},
                new String[] {null, "feelingHappyAnimationState", "scentingAnimationState",
                        "sniffingAnimationState", "diggingAnimationState", "risingAnimationState"},
                "minecraft:sniffer");
    }
}