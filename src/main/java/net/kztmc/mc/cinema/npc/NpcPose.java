package net.kztmc.mc.cinema.npc;

import java.util.List;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * NPCの姿勢。モブごとに選べるものは、EntityProfile#poses で宣言する(選べるものがなければ、欄は出ない)。
 * これは「体全体の姿勢」。将来の Pose(翼・頭などの部位ごとの調整)とは別で、その土台になる。
 */
public enum NpcPose {
    STANDING("立ち", Pose.STANDING),
    CROUCHING("しゃがみ", Pose.CROUCHING),
    SWIMMING("水泳", Pose.SWIMMING),
    FALL_FLYING("滑空", Pose.FALL_FLYING),
    SITTING("座る", Pose.STANDING);   // 座るは、種類ごとのフラグで切り替える(Pose は変えない)

    /** ヒト型のモブ */
    public static final List<NpcPose> HUMANOID = List.of(STANDING, CROUCHING, SWIMMING);

    /** プレイヤー */
    public static final List<NpcPose> PLAYER = List.of(STANDING, CROUCHING, SWIMMING, FALL_FLYING);

    public final String label;
    public final Pose pose;

    NpcPose(String label, Pose pose) {
        this.label = label;
        this.pose = pose;
    }

    public NpcPose next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static NpcPose of(String name) {
        for (NpcPose p : values()) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return STANDING;
    }

    /**
     * エンティティに反映する。冪等(何度呼んでも同じ結果)。
     * @param canSit 「座る」を選べるモブか(座るフラグを、そうでないモブには触らないため)
     */
    public void apply(Entity e, boolean canSit) {
        e.setShiftKeyDown(this == CROUCHING);
        e.setSwimming(this == SWIMMING);
        e.setPose(pose);

        // 滑空(エリトラ): 共有フラグ7と、滑空の経過tick(体を倒す量)
        EntityReflect.invokeFirst(e, List.of("setSharedFlag"), 7, this == FALL_FLYING);
        if (e instanceof LivingEntity) {
            EntityReflect.setInt(e, "fallFlyTicks", this == FALL_FLYING ? 10 : 0);
        }

        // 座る(オオカミ・ネコ・オウムは setInSittingPose、キツネは setSitting)
        if (canSit) {
            EntityReflect.invokeFirst(e, List.of("setInSittingPose", "setSitting"), this == SITTING);
        }
    }
}