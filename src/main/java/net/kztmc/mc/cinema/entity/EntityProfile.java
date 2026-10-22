package net.kztmc.mc.cinema.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.Entity;

/**
 * 1種類(または近い仲間)のエンティティの設定。
 * データだけなら EntityProfile.of(...) に設定をつなげて EntityProfiles に登録する。
 * 動きが必要なら、継承して prepare / applyAnim を実装する。
 *
 * 処理の層:
 *   1. applyAnim: 時刻(tick)から、エンティティの状態を書き込む(entity/animation の部品を使う)
 *   2. applyPose: その上に、部位ごとの調整を重ねる(将来。翼・頭など)
 */
public class EntityProfile {
    private final Set<String> ids;
    private int defaultAnimTick = 0;
    private int animTickMax = 40;
    private boolean hidden;
    private boolean noRender;
    private IconStyle iconStyle;

    protected EntityProfile(String... ids) {
        this.ids = Set.of(ids);
    }

    public static EntityProfile of(String... ids) {
        return new EntityProfile(ids);
    }

    // ---- 設定(つなげて書ける) ----

    /** 追加直後のアニメーション時刻(tick) */
    public EntityProfile defaultAnim(int tick) {
        this.defaultAnimTick = tick;
        return this;
    }

    /** アニメーションのスライダーの上限(tick)。既定は40 */
    public EntityProfile animMax(int tick) {
        this.animTickMax = tick;
        return this;
    }

    /** モブ追加画面に出さない(devモードでは出る) */
    public EntityProfile hidden() {
        this.hidden = true;
        return this;
    }

    /** アイコンを描かず、色つきタイルにする */
    public EntityProfile noRender() {
        this.noRender = true;
        return this;
    }

    public EntityProfile icon(IconStyle style) {
        this.iconStyle = style;
        return this;
    }

    public EntityProfile icon(float fit, float yaw, float pitch) {
        return icon(IconStyle.of(fit, yaw, pitch));
    }

    public EntityProfile icon(float fit, float yaw, float pitch, float roll, float height) {
        return icon(IconStyle.of(fit, yaw, pitch, roll, height));
    }

    // ---- 取得 ----

    public Set<String> ids() {
        return ids;
    }

    public int defaultAnimTick() {
        return defaultAnimTick;
    }

    public int animTickMax() {
        return animTickMax;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isNoRender() {
        return noRender;
    }

    /** 未指定なら null(MobIcons の表を使う) */
    public IconStyle iconStyle() {
        return iconStyle;
    }

    // ---- 動作のフック(必要なものだけ上書きする) ----

    /** 生成直後に1回呼ばれる(NPC用・アイコン用の両方) */
    public void prepare(Entity e) {}

    /**
     * アニメーション時刻の反映。NPCはティックされないので、ここで状態を直接書き込む。
     * time は小数(0.1刻み)。n = floor(time) として、
     * 「n回めの状態」を old、「n+1回めの状態」を current として書き込む(描画側が小数部分で補間する)。
     * tickCount と補間値の設定は、呼び出し側(EntityTweaks)が済ませている。
     */
    public void applyAnim(Entity e, float time) {}

    /**
     * Pose(部位ごとの調整)の反映。applyAnim の後に呼ばれる。今は何もしない。
     * tick で動かした結果の上に、翼・頭などの位置を追加で調整する用。
     */
    public void applyPose(Entity e, float time) {}

    private final List<EntityOption> options = new ArrayList<>();

    /** 右パネルに出す整数スライダーを1つ足す。値は NpcData に保存され、applyOptions で反映する */
    public EntityProfile option(String key, String label, int min, int max, int def) {
        options.add(new EntityOption(key, label, min, max, def));
        return this;
    }

    public List<EntityOption> options() {
        return options;
    }

    /** オプションの反映。保存値のあるキーだけ入っている。NPCの生成時と、スライダーを動かすたびに呼ばれる */
    public void applyOptions(Entity e, Map<String, Integer> values) {}

    /** 位置・向きの設定が終わった後に呼ばれる。位置や向きに合わせて動く部品(エンダードラゴンの当たり判定など)の配置用 */
    public void applyTransform(Entity e) {}

    /** 名前つきの選択肢(スライダーに数字ではなく名前が出る)。0番目から順に番号が付く */
    public EntityProfile choice(String key, String label, int def, String... names) {
        options.add(new EntityOption(key, label, 0, names.length - 1, def, List.of(names)));
        return this;
    }

    /**
     * 設定(options)も受け取る版。既定では、2引数版を呼ぶ。
     * 選択肢で動きを切り替えるプロフィールは、こちらを上書きする。options に無いキーは、宣言した初期値として扱う。
     */
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        applyAnim(e, time);
    }
}