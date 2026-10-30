package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.RegistryHolders;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

/** ネコ。種類(11種)と、飼い猫(首輪の色つき)。 */
public final class CatProfile extends EntityProfile {
    private static final String[] VARIANTS = {"tabby", "black", "red", "siamese", "british_shorthair", "calico",
            "persian", "ragdoll", "white", "jellie", "all_black"};

    public CatProfile() {
        super("minecraft:cat");
        choice("variant", "種類", 0, "タビー", "タキシード", "レッド", "シャム", "ブリティッシュ", "三毛",
                "ペルシャ", "ラグドール", "白", "ジェリー", "黒");
        toggle("tame", "飼い猫", false);
        option("collar", "首輪の色", 0, 15, 14);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int v = Math.max(0, Math.min(VARIANTS.length - 1, options.getOrDefault("variant", 0)));
        Holder<?> variant = RegistryHolders.get(e, Registries.CAT_VARIANT, VARIANTS[v]);
        if (variant != null) {
            EntityReflect.invokeFirst(e, List.of("setVariant"), variant);
        }

        boolean tame = options.getOrDefault("tame", 0) == 1;
        EntityReflect.invokeFirst(e, List.of("setTame"), tame, false);
        if (tame) {
            EntityReflect.invokeFirst(e, List.of("setCollarColor"), DyeColor.byId(options.getOrDefault("collar", 14)));
        }
    }
}