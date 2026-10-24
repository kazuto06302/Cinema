package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ウォーデン。咆哮・嗅ぐ・出てくる・掘る・攻撃・ソニックブームのモーションと、触角の揺れ(0〜10)。
 * 長いモーションは、時間スライダーの0〜40に全体を収める。
 */
public final class WardenProfile extends AnimationModeProfile {
    public WardenProfile() {
        super("state", "モーション",
                new String[] {"待機", "咆哮", "嗅ぐ", "出てくる", "掘る", "攻撃", "ソニックブーム"},
                new String[] {null, "roarAnimationState", "sniffAnimationState", "emergeAnimationState",
                        "diggingAnimationState", "attackAnimationState", "sonicBoomAnimationState"},
                // モーションの長さ(秒)。推測値(vanilla の記憶)
                new float[] {0f, 4.2f, 4.16f, 6.68f, 5.0f, 0.33f, 3.0f},
                "minecraft:warden");
        option("tendril", "触角", 0, 10, 0);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        super.applyAnim(e, time, options);
        int t = options.getOrDefault("tendril", 0);
        EntityReflect.setInt(e, "tendrilAnimationO", t);
        EntityReflect.setInt(e, "tendrilAnimation", t);
    }
}