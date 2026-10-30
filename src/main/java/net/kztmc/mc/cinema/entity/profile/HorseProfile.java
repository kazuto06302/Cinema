package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/** 馬。毛色(7種)と模様(5種)。防具は「胴(防具)」、鞍は「脚(鞍)」の装備スロットに入れる。 */
public final class HorseProfile extends EntityProfile {
    private static final String[] COLORS = {"WHITE", "CREAMY", "CHESTNUT", "BROWN", "BLACK", "GRAY", "DARK_BROWN"};
    private static final String[] MARKINGS = {"NONE", "WHITE", "WHITE_FIELD", "WHITE_DOTS", "BLACK_DOTS"};

    public HorseProfile() {
        super("minecraft:horse");
        dropdownChoice("color", "毛色", 0, "白", "クリーム", "栗毛", "茶", "黒", "灰", "濃い茶");
        dropdownChoice("markings", "模様", 0, "なし", "白", "白の広がり", "白い斑点", "黒い斑点");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int c = Math.max(0, Math.min(COLORS.length - 1, options.getOrDefault("color", 0)));
        int m = Math.max(0, Math.min(MARKINGS.length - 1, options.getOrDefault("markings", 0)));
        EntityReflect.invokeWithEnumArgs(e, "setVariantAndMarkings", COLORS[c], MARKINGS[m]);
    }
}