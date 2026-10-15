package net.kztmc.mc.cinema.npc;

import net.minecraft.world.entity.Pose;

/** サムネ撮影でよく使うポーズ。増やすときは enum に足すだけ(保存は名前文字列)。 */
public enum NpcPose {
    STANDING("立ち", Pose.STANDING),
    CROUCHING("しゃがみ", Pose.CROUCHING),
    SWIMMING("水泳", Pose.SWIMMING);

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
}
