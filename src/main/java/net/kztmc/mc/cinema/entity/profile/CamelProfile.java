package net.kztmc.mc.cinema.entity.profile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

/** ラクダ・ラクダハスク。アイドル・座る・座っている・立ち上がる・ダッシュ。長い動作は、時間スライダーの0〜40に全体を収める。 */
public final class CamelProfile extends AnimationModeProfile {
    public CamelProfile() {
        super("state", "動作",
                new String[] {"アイドル", "座る", "座っている", "立ち上がる", "ダッシュ"},
                new String[] {"idleAnimationState", "sitAnimationState", "sitPoseAnimationState",
                        "sitUpAnimationState", "dashAnimationState"},
                // 動作の長さ(秒)。0はループなので、実際の速さのまま。推測値(vanilla の記憶)
                new float[] {0f, 2.0f, 0f, 2.6f, 0.625f},
                "minecraft:camel", "minecraft:camel_husk");
        walkKeyframe(1.5f, 2.0f);   // 推測値
    }

    @Override
    protected void onMode(Entity e, int mode, float time) {
        // 座る系は、座った姿勢(体の高さも変わる)にする
        e.setPose(mode == 1 || mode == 2 ? Pose.SITTING : Pose.STANDING);
    }
}