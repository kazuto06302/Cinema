package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** ラマ・行商人のラマ。毛色(4種)。カーペットは、「胴(防具)」の装備スロットに入れる。 */
public final class LlamaProfile extends EntityProfile {
    private static final String[] COLORS = {"CREAMY", "WHITE", "BROWN", "GRAY"};

    public LlamaProfile() {
        super("minecraft:llama", "minecraft:trader_llama");
        dropdownChoice("color", "毛色", 0, "クリーム", "白", "茶", "灰");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int c = Math.max(0, Math.min(COLORS.length - 1, options.getOrDefault("color", 0)));
        EntityReflect.invokeWithEnumArg(e, "setVariant", COLORS[c]);
    }
}