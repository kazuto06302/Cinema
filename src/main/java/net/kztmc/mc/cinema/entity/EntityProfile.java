package net.kztmc.mc.cinema.entity;

import java.util.*;
import java.util.function.Predicate;

import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

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
    private boolean humanoid;
    private List<NpcPose> poses = List.of();
    private boolean walks = true;
    private float walkPhase;
    private float walkSpeedMax = 1f;
    private boolean noHitbox;
    private boolean animalGear;
    private float walkCycleSeconds;
    private float walkMaxAnimationSpeed = 1f;
    private float walkKeyframeIntensity = 1f;
    private String heldItemLabel;
    private Predicate<ItemStack> heldItemFilter = stack -> true;
    private final Set<String> hiddenStates = new HashSet<>();
    private boolean noScale;
    private EquipmentMode equipment;

    protected EntityProfile(String... ids) {
        this.ids = Set.of(ids);
    }
    public static EntityProfile of(String... ids) {
        return new EntityProfile(ids);
    }
    public Set<String> ids() {
        return ids;
    }
    public void prepare(Entity e) {}


    //アニメーション
    public EntityProfile defaultAnim(int tick) {
        this.defaultAnimTick = tick;
        return this;
    }
    public EntityProfile animMax(int tick) {
        this.animTickMax = tick;
        return this;
    }
    public int defaultAnimTick() {
        return defaultAnimTick;
    }
    public int animTickMax() {
        return animTickMax;
    }

    //アイコン
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

    public boolean isHidden() {
        return hidden;
    }
    public EntityProfile hidden() {
        this.hidden = true;
        return this;
    }
    public boolean isNoRender() {
        return noRender;
    }
    public EntityProfile noRender() {
        this.noRender = true;
        return this;
    }
    public IconStyle iconStyle() {
        return iconStyle;
    }

    /**
     * アニメーション時刻の反映。NPCはティックされないので、ここで状態を直接書き込む。
     * time は小数(0.1刻み)。n = floor(time) として、
     * 「n回めの状態」を old、「n+1回めの状態」を current として書き込む(描画側が小数部分で補間する)。
     * tickCount と補間値の設定は、呼び出し側(EntityTweaks)が済ませている。
     */
    public void applyAnim(Entity e, float time) {}
    public void applyPose(Entity e, float time) {}
    public void applyTransform(Entity e) {}
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        applyAnim(e, time);
    }

    //humanoid
    public EntityProfile humanoid() {
        this.humanoid = true;
        if (poses.isEmpty()) poses = NpcPose.HUMANOID;
        return this;
    }
    public boolean isHumanoid() {
        return humanoid;
    }

    //ポーズ
    public EntityProfile poses(NpcPose... poses) {
        this.poses = List.of(poses);
        return this;
    }
    public List<NpcPose> poses() {
        return poses;
    }

    //ヒットボックス
    public EntityProfile noHitbox() {
        this.noHitbox = true;
        return this;
    }
    public boolean hasNoHitbox() {
        return noHitbox;
    }

    //オプション
    private final List<EntityOption> options = new ArrayList<>();
    public List<EntityOption> options() {
        return options;
    }
    public void applyOptions(Entity e, Map<String, Integer> values) {}

    //選択肢
    public EntityProfile option(String key, String label, int min, int max, int def) {
        options.add(new EntityOption(key, label, min, max, def));
        return this;
    }
    public EntityProfile dropdownChoice(String key, String label, int def, String... names) {
        options.add(new EntityOption(key, label, 0, names.length - 1, def, List.of(names), EntityOption.Kind.DROPDOWN));
        return this;
    }
    public EntityProfile toggle(String key, String label, boolean def) {
        options.add(new EntityOption(key, label, 0, 1, def ? 1 : 0, List.of("オフ", "オン"), EntityOption.Kind.CHECKBOX));
        return this;
    }
    public EntityProfile choice(String key, String label, int def, String... names) {
        options.add(new EntityOption(key, label, 0, names.length - 1, def, List.of(names)));
        return this;
    }
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {}

    //動物
    public EntityProfile animalGear() {
        this.animalGear = true;
        return this;
    }
    public boolean hasAnimalGear() {
        return equipmentMode() == EquipmentMode.ANIMAL;
    }

    //歩行
    public EntityProfile noWalk() {
        this.walks = false;
        return this;
    }
    public boolean walks() {
        return walks;
    }
    public EntityProfile walkKeyframe(float cycleSeconds, float maxAnimationSpeed) {
        this.walkCycleSeconds = cycleSeconds;
        this.walkMaxAnimationSpeed = maxAnimationSpeed;
        return this;
    }
    public float walkCyclePosition() {
        return walkCycleSeconds <= 0f ? 0f : walkCycleSeconds * 1000f / (50f * walkMaxAnimationSpeed);
    }
    public float walkCycleSeconds() {
        return walkCycleSeconds;
    }
    public float walkMaxAnimationSpeed() {
        return walkMaxAnimationSpeed;
    }
    public void setWalkCycleSeconds(float seconds) {
        this.walkCycleSeconds = seconds;
    } //dev
    public EntityProfile walkSpeedMax(float max) {
        this.walkSpeedMax = max;
        return this;
    }
    public float walkPhase() {
        return walkPhase;
    }
    public EntityProfile walkPhase(float limbSwingOffset) {
        this.walkPhase = limbSwingOffset;
        return this;
    }
    public float walkSpeedMax() {
        return walkSpeedMax;
    }
    public EntityProfile walkKeyframe(float cycleSeconds, float maxAnimationSpeed, float intensity) {
        this.walkKeyframeIntensity = intensity;
        return walkKeyframe(cycleSeconds, maxAnimationSpeed);
    }
    public float walkKeyframeIntensity() {
        return walkKeyframeIntensity;
    }
    public void setWalkKeyframeIntensity(float intensity) {
        this.walkKeyframeIntensity = intensity;
    }

    //手持ち
    public void applyData(Entity e, NpcData d) {}
    public EntityProfile heldItem(String label, Predicate<ItemStack> filter) {
        this.heldItemLabel = label;
        this.heldItemFilter = filter;
        return this;
    }
    public String heldItemLabel() {
        return heldItemLabel;
    }
    public boolean acceptsHeld(ItemStack stack) {
        return heldItemFilter.test(stack);
    }

    //状態
    public EntityProfile hideStates(String... keys) {
        hiddenStates.addAll(List.of(keys));
        return this;
    }
    public boolean isStateHidden(String key) {
        return hiddenStates.contains(key);
    }

    //サイズ
    public EntityProfile noScale() {
        this.noScale = true;
        return this;
    }
    public boolean hasNoScale() {
        return noScale;
    }

    public EntityProfile listed(boolean v) { this.hidden = !v; return this; }
    public EntityProfile rendersIcon(boolean v) { this.noRender = !v; return this; }
    public EntityProfile walking(boolean v) { this.walks = v; return this; }
    public EntityProfile humanoid(boolean v) { if (v) return humanoid(); this.humanoid = false; return this; }
    public EntityProfile scalable(boolean v) { this.noScale = !v; return this; }
    public EntityProfile hitbox(boolean v) { this.noHitbox = !v; return this; }
    public EntityProfile equipment(EquipmentMode mode) { this.equipment = mode; return this; }

    /** メインハンドだけにするときの、スロットの名前を指定する(空なら変えない) */
    public EntityProfile heldLabel(String label) {
        if (label != null && !label.isBlank()) this.heldItemLabel = label;
        return this;
    }

    /** 装備欄の形。表の指定が優先。なければ、コードの設定(heldItem・animalGear)から決める */
    public EquipmentMode equipmentMode() {
        if (equipment != null) return equipment;
        if (heldItemLabel != null) return EquipmentMode.MAIN;
        return animalGear ? EquipmentMode.ANIMAL : EquipmentMode.FULL;
    }

    public boolean isMainHandOnly() {
        return equipmentMode() == EquipmentMode.MAIN;
    }

    public String heldItemLabelOrDefault() {
        return heldItemLabel != null ? heldItemLabel : "持ち物";
    }
}