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

/** オオカミ。種類(9種)、飼い犬(首輪の色つき)、攻撃モード(怒っている)。 */
public final class WolfProfile extends EntityProfile {
    private static final String[] VARIANTS =
            {"pale", "spotted", "snowy", "black", "ashen", "rusty", "woods", "chestnut", "striped"};

    public WolfProfile() {
        super("minecraft:wolf");
        choice("variant", "種類", 0,
                "ペール", "スポット", "スノー", "ブラック", "アッシュ", "ラスティ", "ウッズ", "チェスナット", "ストライプ");
        choice("tame", "飼い犬", 0, "野生", "飼い犬");
        option("collar", "首輪の色", 0, 15, 14);   // 14 = 赤
        dropdownChoice("attack", "攻撃モード", 0, "なし", "あり");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int v = Math.max(0, Math.min(VARIANTS.length - 1, options.getOrDefault("variant", 0)));
        Holder<?> variant = RegistryHolders.get(e, Registries.WOLF_VARIANT, VARIANTS[v]);
        if (variant != null) {
            EntityReflect.invokeFirst(e, List.of("setVariant"), variant);
        }

        boolean tame = options.getOrDefault("tame", 0) == 1;
        EntityReflect.invokeFirst(e, List.of("setTame"), tame, false);
        if (tame) {
            EntityReflect.invokeFirst(e, List.of("setCollarColor"), DyeColor.byId(options.getOrDefault("collar", 14)));
        }

        // 怒っている状態(目つきと体勢が変わる)
        boolean attack = options.getOrDefault("attack", 0) == 1;
        EntityReflect.invokeFirst(e, List.of("setRemainingPersistentAngerTime"), attack ? 400 : 0);
    }
}