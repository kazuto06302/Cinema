package net.kztmc.mc.cinema.entity;

import java.util.Set;
import net.minecraft.world.entity.Entity;

/**
 * 1種類(または近い仲間)のエンティティの設定。
 * データだけなら EntityProfile.of(...) に設定をつなげて EntityProfiles に登録する。
 * 動きが必要なら、継承して prepare / applyAnim を実装する。
 */
public class EntityProfile {
    private final Set<String> ids;
    private int defaultAnimTick = 0;
    private int animTickMax = 20;
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

    /** アニメーションのスライダーの上限(tick)。既定は20 */
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

    /** 未指定なら null(従来の MobIcons の表を使う) */
    public IconStyle iconStyle() {
        return iconStyle;
    }

    // ---- 動作のフック(必要なものだけ上書きする) ----

    /** 生成直後に1回呼ばれる(NPC用・アイコン用の両方) */
    public void prepare(Entity e) {}

    /**
     * アニメーション時刻の反映。NPCはティックされないので、ここで状態を直接書き込む。
     * tickCount の設定は、呼び出し側が済ませている。
     */
    public void applyAnim(Entity e, int tick) {}
}