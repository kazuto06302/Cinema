package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * アイアンゴーレム。攻撃は、残り tick(10→0)で腕を振り上げて振り下ろす(時間0〜10で進む)。
 * 花を差し出す姿勢も選べる。
 */
public final class IronGolemProfile extends EntityProfile {
    private static final int ATTACK_TICKS = 10;
    private static final int OFFER_FLOWER_TICKS = 400;

    public IronGolemProfile() {
        super("minecraft:iron_golem");
        toggle("attack", "攻撃", false);
        toggle("flower", "花を差し出す",false);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int n = (int) Math.floor(time);
        boolean attack = options.getOrDefault("attack", 0) == 1;
        boolean flower = options.getOrDefault("flower", 0) == 1;
        // 描画は「残り tick - 補間値」を使うので、10 - n で、進み具合が 10 - time になる
        EntityReflect.setInt(e, "attackAnimationTick", attack ? Math.max(0, ATTACK_TICKS - n) : 0);
        EntityReflect.setInt(e, "offerFlowerTick", flower ? OFFER_FLOWER_TICKS : 0);
    }
}