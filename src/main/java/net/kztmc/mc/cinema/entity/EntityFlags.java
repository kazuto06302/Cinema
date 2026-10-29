package net.kztmc.mc.cinema.entity;

import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 全モブ共通の状態: 燃えている・光っている・子ども・ダメージ(赤く染まる)。
 * NPCごとの options に、0/1で保存される。
 */
public final class EntityFlags {
    private EntityFlags() {}

    public static final String FIRE = "fire";
    public static final String GLOW = "glow";
    public static final String BABY = "baby";
    public static final String DAMAGE = "damage";

    /**
     * NPCの状態に反映する(位置を決める前に呼ぶ。子どもは、大きさが変わるため)。
     * @param highlighted 編集画面で選択中か(選択中は、光るアウトラインを出す)
     */
    public static void apply(Entity e, Map<String, Integer> options, boolean highlighted) {
        // 燃えている: クライアントでは、共有フラグ0で決まる
        EntityReflect.invokeFirst(e, List.of("setSharedFlag"), 0, options.getOrDefault(FIRE, 0) == 1);

        applyGlow(e, options, highlighted);

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

    /** 光っている(NPCの設定)か、選択中なら、光るアウトラインを出す */
    public static void applyGlow(Entity e, Map<String, Integer> options, boolean highlighted) {
        boolean glow = options.getOrDefault(GLOW, 0) == 1 || highlighted;
        EntityReflect.invokeFirst(e, List.of("setSharedFlag"), 6, glow);   // 6 = 光る(アウトライン)
    }
}