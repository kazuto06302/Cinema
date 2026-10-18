package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.profile.animation.FlapAnimation;
import net.minecraft.world.entity.Entity;

/** ニワトリ。羽ばたきは、tickCount ではなく flap / flapSpeed で動く。 */
public final class ChickenProfile extends EntityProfile {
    public ChickenProfile() {
        super("minecraft:chicken");
        defaultAnim(1);   // 羽がいちばん開く位置
    }

    @Override
    public void applyAnim(Entity e, float time) {
        FlapAnimation.apply(e, time);
    }
}