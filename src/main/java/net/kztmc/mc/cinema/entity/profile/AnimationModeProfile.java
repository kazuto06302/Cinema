package net.kztmc.mc.cinema.entity.profile;

import java.util.Locale;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.profile.animation.AnimationStates;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 「状態」を選ぶと、対応する AnimationState だけが再生され、時間スライダーでその動きを進められるプロフィールの共通部分。
 * 動作が長いもの(ラクダの立ち上がり、ウォーデンの出現など)は、動作の長さ(秒)を渡すと、
 * 時間スライダーの0〜40が、動作の全体(最後は元の姿勢)に対応するように伸縮する。
 * 0または省略の状態(アイドルなどのループ)は、実際の速さのまま。
 * 経過時間は、描画状態の ageInTicks を書き換えて決める(開始tickは整数で、誤差が出るため)。
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
        this.seconds = seconds != null ? seconds.clone() : new float[fields.length];
        choice(optionKey, label, 0, names);
    }

    private int modeOf(Map<String, Integer> options) {
        return Mth.clamp(options.getOrDefault(optionKey, 0), 0, fields.length - 1);
    }

    private float scaleOf(int mode) {
        return seconds[mode] > 0f ? seconds[mode] * 20f / FIT_TICKS : 1f;
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int mode = modeOf(options);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) continue;
            if (i == mode) {
                AnimationStates.play(e, fields[i]);
            } else {
                AnimationStates.stop(e, fields[i]);
            }
        }
        onMode(e, mode, time);
    }

    /** 再生中の動作の経過時間を、描画状態の ageInTicks として書き込む(伸縮込み) */
    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        int mode = modeOf(options);
        if (fields[mode] != null) {
            EntityReflect.setFloat(state, "ageInTicks", time * scaleOf(mode));
        }
    }

    /** 状態に合わせた、AnimationState 以外の設定(フラグなど) */
    protected void onMode(Entity e, int mode, float time) {}

    // ---- devモードの調整用 ----

    public String optionKey() {
        return optionKey;
    }

    public boolean hasField(int mode) {
        return mode >= 0 && mode < fields.length && fields[mode] != null;
    }

    public float secondsOf(int mode) {
        return seconds[mode];
    }

    public void setSeconds(int mode, float value) {
        seconds[mode] = value;
    }

    /** コードに貼れる「動作の長さ」の配列 */
    public String secondsLine() {
        StringBuilder sb = new StringBuilder("new float[] {");
        for (int i = 0; i < seconds.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.ROOT, "%.2ff", seconds[i]));
        }
        return sb.append("}").toString();
    }
}