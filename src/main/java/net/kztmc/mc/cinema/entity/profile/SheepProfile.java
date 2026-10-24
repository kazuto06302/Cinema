package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

/**
 * ヒツジ。草を食べる動きは、残り tick(40→0)で決まる(時間0〜40で進む)。
 * 毛の色(0〜15。染料の番号)と、毛刈り済みも選べる。
 */
public final class SheepProfile extends EntityProfile {
    private static final int EAT_TICKS = 40;

    public SheepProfile() {
        super("minecraft:sheep");
        choice("eat", "草を食べる", 0, "しない", "する");
        option("color", "毛の色", 0, 15, 0);
        dropdownChoice("sheared", "毛刈り", 0, "されていない", "されている");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int n = (int) Math.floor(time);
        boolean eat = options.getOrDefault("eat", 0) == 1;
        // 描画は「残り tick - 補間値」を使うので、40 - n で、進み具合が 40 - time になる
        EntityReflect.setInt(e, "eatAnimationTick", eat ? Math.max(0, EAT_TICKS - n) : 0);
        EntityReflect.invokeFirst(e, List.of("setColor"), DyeColor.byId(options.getOrDefault("color", 0)));
        EntityReflect.invokeFirst(e, List.of("setSheared"), options.getOrDefault("sheared", 0) == 1);
    }
}