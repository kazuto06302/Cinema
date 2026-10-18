package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.profile.animation.FlapAnimation;
import net.minecraft.world.entity.Entity;

/** オウム。羽ばたきは、ニワトリと同じ仕組み。 */
public final class ParrotProfile extends EntityProfile {
    public ParrotProfile() {
        super("minecraft:parrot");
        defaultAnim(1);
    }

    @Override
    public void applyAnim(Entity e, int tick) {
        FlapAnimation.apply(e, tick);
    }
}