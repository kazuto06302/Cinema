package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

/** クッション。色(染料の番号0〜15)。メソッド名は推測なので、効かなければ /cinema fields cushion で確認する。 */
public final class CushionProfile extends EntityProfile {
    public CushionProfile() {
        super("minecraft:cushion");
        option("color", "色", 0, 15, 0);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        EntityReflect.invokeFirst(e, List.of("setColor", "setDyeColor"), DyeColor.byId(options.getOrDefault("color", 0)));
    }
}