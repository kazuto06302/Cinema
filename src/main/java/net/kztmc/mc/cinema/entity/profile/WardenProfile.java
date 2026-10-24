package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ウォーデン。咆哮・嗅ぐ・出てくる・掘る・攻撃・ソニックブームのモーションと、触角の揺れ(0〜10)。 */
public final class WardenProfile extends AnimationModeProfile {
    public WardenProfile() {
        super("state", "モーション",
                new String[] {"待機", "咆哮", "嗅ぐ", "出てくる", "掘る", "攻撃", "ソニックブーム"},
                new String[] {null, "roarAnimationState", "sniffAnimationState", "emergeAnimationState",
                        "diggingAnimationState", "attackAnimationState", "sonicBoomAnimationState"},
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