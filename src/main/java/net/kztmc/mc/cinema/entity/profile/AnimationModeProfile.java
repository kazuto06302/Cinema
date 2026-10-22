package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.profile.animation.AnimationStates;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** 「状態」を選ぶと、対応する AnimationState だけが再生され、時間スライダーでその動きを進められるプロフィールの共通部分。 */
public abstract class AnimationModeProfile extends EntityProfile {
    private final String optionKey;
    private final String[] fields;

    /**
     * @param names  状態の名前(スライダーに出る)
     * @param fields 状態ごとの AnimationState のフィールド名。null は「何も再生しない」
     */
    protected AnimationModeProfile(String optionKey, String label, String[] names, String[] fields, String... ids) {
        super(ids);
        this.optionKey = optionKey;
        this.fields = fields;
        choice(optionKey, label, 0, names);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = Mth.clamp(options.getOrDefault(optionKey, 0), 0, fields.length - 1);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) continue;
            if (i == mode) {
                AnimationStates.play(e, fields[i], time);
            } else {
                AnimationStates.stop(e, fields[i]);
            }
        }
        onMode(e, mode, time);
    }

    /** 状態に合わせた、AnimationState 以外の設定(フラグなど) */
    protected void onMode(Entity e, int mode, float time) {}
}