package net.kztmc.mc.cinema.entity.profile;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.profile.animation.AnimationStates;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;

/**
 * AnimationState のフィールド名が分からないモブ用。クラスの AnimationState をすべて探して、
 * 「アニメ1」「アニメ2」…の番号で選べるようにする。対応は、初回にログへ出る。
 * 長いアニメーションは、「再生倍率」を上げると、時間スライダーの範囲に収まる(×4なら、スライダー40tickで160tickぶん進む)。
 */
public class AutoAnimationProfile extends EntityProfile {
    private static final int MAX_STATES = 8;
    private static final int[] SPEEDS = {1, 2, 3, 4, 6, 8, 12, 16};
    private static final Map<Class<?>, List<String>> FIELDS = new HashMap<>();

    protected AutoAnimationProfile(String... ids) {
        super(ids);
        String[] names = new String[MAX_STATES + 1];
        names[0] = "なし";
        for (int i = 1; i <= MAX_STATES; i++) names[i] = "アニメ" + i;
        choice("anim", "動作", 0, names);
        choice("speed", "再生倍率", 0, "×1", "×2", "×3", "×4", "×6", "×8", "×12", "×16");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        List<String> fields = discover(e);
        int mode = options.getOrDefault("anim", 0);
        for (int i = 0; i < fields.size(); i++) {
            if (i + 1 == mode) {
                AnimationStates.play(e, fields.get(i));
            } else {
                AnimationStates.stop(e, fields.get(i));
            }
        }
    }

    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        if (options.getOrDefault("anim", 0) > 0) {
            int speed = SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, options.getOrDefault("speed", 0)))];
            EntityReflect.setFloat(state, "ageInTicks", time * speed);
        }
    }

    private static List<String> discover(Entity e) {
        return FIELDS.computeIfAbsent(e.getClass(), c -> {
            List<String> out = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && AnimationState.class.isAssignableFrom(f.getType())) {
                        out.add(f.getName());
                    }
                }
            }
            List<String> listing = new ArrayList<>();
            for (int i = 0; i < out.size(); i++) listing.add("アニメ" + (i + 1) + " = " + out.get(i));
            Cinema.LOGGER.info("[Cinema] animation states of {}: {}", c.getSimpleName(), listing);
            return out;
        });
    }
}