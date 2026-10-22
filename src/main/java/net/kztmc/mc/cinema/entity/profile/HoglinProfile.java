package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ホグリンとゾグリン。攻撃の頭突きは、残り tick(10→0)で決まる。
 * 時間が0〜10の間に頭を下げて突き上げる(5で最も下がる)。
 */
public final class HoglinProfile extends EntityProfile {
    private static final int ATTACK_TICKS = 10;

    public HoglinProfile() {
        super("minecraft:hoglin", "minecraft:zoglin");
        choice("attack", "攻撃", 0, "しない", "する");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int n = (int) Math.floor(time);
        boolean attack = options.getOrDefault("attack", 0) == 1;
        EntityReflect.setInt(e, "attackAnimationRemainingTicks", attack ? Math.max(0, ATTACK_TICKS - n) : 0);
    }
}