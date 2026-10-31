package net.kztmc.mc.cinema.entity;

import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 全モブ共通の状態: 大きさ・燃えている・光っている・子ども・ダメージ(赤く染まる)。
 * NPCごとの options に保存される(大きさは%の整数、ほかは0/1)。
 */
public final class EntityFlags {
    private EntityFlags() {}

    public static final String SCALE = "scale";
    public static final String FIRE = "fire";
    public static final String GLOW = "glow";
    public static final String BABY = "baby";
    public static final String DAMAGE = "damage";

    /** NPCの状態に反映する(位置を決める前に呼ぶ。大きさと子どもは、当たり判定の大きさが変わるため) */
    public static void apply(Entity e, Map<String, Integer> options) {
        // 大きさ(%)。生き物の属性 SCALE で決まる。描画も当たり判定も、これに従う
        if (e instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attributes.SCALE);
            if (scale != null) {
                double value = options.getOrDefault(SCALE, 100) / 100.0;
                if (scale.getBaseValue() != value) {
                    scale.setBaseValue(value);
                    e.refreshDimensions();
                }
            }
        }

        // 燃えている: クライアントでは、共有フラグ0で決まる
        EntityReflect.invokeFirst(e, List.of("setSharedFlag"), 0, options.getOrDefault(FIRE, 0) == 1);

        // 光っている: 共有フラグ6(バニラの光るアウトライン)
        EntityReflect.invokeFirst(e, List.of("setSharedFlag"), 6, options.getOrDefault(GLOW, 0) == 1);

        // 子ども: setBaby を持つモブ(ゾンビ・動物など)だけ。Mob の空の setBaby は、除く
        if (EntityReflect.hasMethodBefore(e, "setBaby", Mob.class)) {
            EntityReflect.invokeFirst(e, List.of("setBaby"), options.getOrDefault(BABY, 0) == 1);
        }

        // ダメージ: hurtTime > 0 で、赤く染まる。NPCはティックされないので、そのまま残る
        if (e instanceof LivingEntity living) {
            boolean hurt = options.getOrDefault(DAMAGE, 0) == 1;
            living.hurtDuration = hurt ? 10 : 0;
            living.hurtTime = hurt ? 10 : 0;
        }
    }
}