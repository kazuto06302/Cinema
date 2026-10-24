package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;

import net.kztmc.mc.cinema.entity.EntityMotion;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ウーパールーパー。姿勢は、BinaryAnimator(0〜1の切り替え。ticks / ticksOld が進み具合)の組み合わせで決まる。
 * ティックされないので、目標の値を直接書き込む。色は enum の定数名で指定する。
 */
public final class AxolotlProfile extends EntityProfile {
    private static final float CYCLE_TICKS = 40f;
    private static final String[] COLORS = {"LUCY", "WILD", "GOLD", "CYAN", "BLUE"};

    public AxolotlProfile() {
        super("minecraft:axolotl");
        choice("mode", "状態", 0, "水中", "地上", "死んだふり");
        choice("color", "色", 0, "ピンク", "茶", "金", "シアン", "青");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = options.getOrDefault("mode", 0);
        setFactor(e, "inWaterAnimator", mode == 0 || mode == 2 ? 1f : 0f);
        setFactor(e, "onGroundAnimator", mode == 1 ? 1f : 0f);
        setFactor(e, "playingDeadAnimator", mode == 2 ? 1f : 0f);
        setFactor(e, "movingAnimator", options.getOrDefault(EntityMotion.WALK, 0) != 0 ? 1f : 0f);

        int color = Math.max(0, Math.min(COLORS.length - 1, options.getOrDefault("color", 0)));
        EntityReflect.invokeWithEnumArg(e, "setVariant", COLORS[color]);
    }

    private static void setFactor(Entity e, String field, float factor) {
        Object animator = EntityReflect.getObject(e, field);
        if (animator == null) return;
        int length = EntityReflect.getIntQuiet(animator, "animationLength", 10);
        int ticks = Math.round(factor * length);
        EntityReflect.setInt(animator, "ticks", ticks);
        EntityReflect.setInt(animator, "ticksOld", ticks);
    }

    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        float walk = options.getOrDefault(EntityMotion.WALK, 0) / 100f;
        if (walk != 0f) {
            // 足・尾・えらの動きは ageInTicks で決まるので、歩行スライダーの値で置き換える
            EntityReflect.setFloat(state, "ageInTicks", Math.abs(walk) * CYCLE_TICKS);
        }
    }
}