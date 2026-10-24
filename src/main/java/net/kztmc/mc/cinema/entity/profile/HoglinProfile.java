package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ホグリンとゾグリン。頭突きのアニメーションを、常に時間で進める(選択肢なし)。
 * 描画は残り tick(10〜0)で頭の傾きを決めるので、10tickで1周する。
 */
public final class HoglinProfile extends EntityProfile {
    private static final int ATTACK_TICKS = 10;

    public HoglinProfile() {
        super("minecraft:hoglin", "minecraft:zoglin");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int n = (int) Math.floor(time);
        EntityReflect.setInt(e, "attackAnimationRemainingTicks", ATTACK_TICKS - (n % ATTACK_TICKS));
    }
}