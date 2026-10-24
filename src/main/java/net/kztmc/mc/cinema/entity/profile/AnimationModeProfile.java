package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.profile.animation.AnimationStates;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 「状態」を選ぶと、対応する AnimationState だけが再生され、時間スライダーでその動きを進められるプロフィールの共通部分。
 * 動作が長いもの(ラクダの立ち上がり、ウォーデンの出現など)は、時間スライダーの全範囲(0〜40tick)に収まるように、
 * 動作の長さ(秒)を渡す。渡した状態は、スライダーの0〜40が、動作の全体に対応する。
 * 0または省略の状態(アイドルなどのループ)は、実際の速さのまま。
 */
public abstract class AnimationModeProfile extends EntityProfile {
    /** 時間スライダーの上限(tick)。EntityProfile の animTickMax の既定と同じ */
    private static final float FIT_TICKS = 40f;

    private final String optionKey;
    private final String[] fields;
    private final float[] seconds;

    protected AnimationModeProfile(String optionKey, String label, String[] names, String[] fields, String... ids) {
        this(optionKey, label, names, fields, null, ids);
    }

    /**
     * @param names   状態の名前(スライダーに出る)
     * @param fields  状態ごとの AnimationState のフィールド名。null は「何も再生しない」
     * @param seconds 状態ごとの動作の長さ(秒)。null や0は、実際の速さのまま
     */
    protected AnimationModeProfile(String optionKey, String label, String[] names, String[] fields,
                                   float[] seconds, String... ids) {
        super(ids);
        this.optionKey = optionKey;
        this.fields = fields;
        this.seconds = seconds;
        choice(optionKey, label, 0, names);
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = Mth.clamp(options.getOrDefault(optionKey, 0), 0, fields.length - 1);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) continue;
            if (i == mode) {
                float scale = seconds != null && seconds[i] > 0f ? seconds[i] * 20f / FIT_TICKS : 1f;
                AnimationStates.play(e, fields[i], time * scale);
            } else {
                AnimationStates.stop(e, fields[i]);
            }
        }
        onMode(e, mode, time);
    }

    /** 状態に合わせた、AnimationState 以外の設定(フラグなど) */
    protected void onMode(Entity e, int mode, float time) {}
}