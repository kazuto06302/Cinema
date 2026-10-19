package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/**
 * スライムとマグマキューブ。潰れ・伸びは squish で決まる。
 * バニラの更新式を使い、「8tickで飛び立ち、22tickで着地する」1回のジャンプを再現した結果を書き込む。
 *   squish > 0 : 縦に伸びる(飛び立ち)
 *   squish < 0 : 横に潰れる(着地)
 */
public final class SlimeProfile extends EntityProfile {
    /** 飛び立つtick(この時点で目標が +1.0 になる) */
    private static final int LAUNCH_TICK = 8;

    /** 着地するtick(この時点で目標が -0.5 になる) */
    private static final int LAND_TICK = 22;

    /**
     * 潰れ・伸びの強さの倍率。1.0がバニラ。
     * バニラは控えめ(最大でも約±0.3)なので、サムネ向けにもっと強調したいときは 2.0 などに上げる。
     */
    private static final float AMPLITUDE = 1.0f;

    private static final class State {
        float squish;
        float target;

        /** tick回めのティック(1始まり)をバニラと同じ順序で進める */
        void step(int tick) {
            squish += (target - squish) * 0.5f;
            if (tick == LAUNCH_TICK) {
                target = 1.0f;          // 地面を離れた
            } else if (tick == LAND_TICK) {
                target = -0.5f;         // 着地した
            }
            target *= 0.6f;             // decreaseSquish
        }
    }

    public SlimeProfile() {
        super("minecraft:slime", "minecraft:magma_cube", "minecraft:sulfur_cube");
        defaultAnim(0);
        option("size", "サイズ", 1, 8, 1);   // バニラは 1(小)・2(中)・4(大)。それ以外も指定できる
    }

    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        State s = new State();
        for (int i = 1; i <= Math.min(n, 400); i++) s.step(i);

        float oldSquish = s.squish;
        s.step(n + 1);

        EntityReflect.setFloat(e, "oSquish", oldSquish * AMPLITUDE);
        EntityReflect.setFloat(e, "squish", s.squish * AMPLITUDE);
        EntityReflect.setFloat(e, "targetSquish", s.target * AMPLITUDE);
    }

    @Override
    public void applyOptions(Entity e, Map<String, Integer> values) {
        Integer size = values.get("size");
        if (size != null) {
            // Slime#setSize(int, boolean)。当たり判定の大きさも一緒に変わる
            EntityReflect.invoke(e, "setSize", new Class<?>[] {int.class, boolean.class}, size, false);
        }
    }
}