package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * ウィザー。青い(召喚直後の無敵状態のテクスチャ)と、装甲(体力が半分以下になると出る、渦巻く膜)。
 * 無敵の残りtickが80を超えると、青いテクスチャのまま点滅しない。
 */
public final class WitherProfile extends EntityProfile {
    private static final int INVULNERABLE_TICKS = 150;

    public WitherProfile() {
        super("minecraft:wither");
        toggle("blue", "青い(無敵状態)", false);
        toggle("armor", "装甲(体力が半分以下)", false);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        boolean blue = options.getOrDefault("blue", 0) == 1;
        EntityReflect.invokeFirst(e, List.of("setInvulnerableTicks"), blue ? INVULNERABLE_TICKS : 0);

        if (e instanceof LivingEntity living) {
            boolean armor = options.getOrDefault("armor", 0) == 1;
            living.setHealth(armor ? living.getMaxHealth() / 2f - 1f : living.getMaxHealth());
        }
    }
}