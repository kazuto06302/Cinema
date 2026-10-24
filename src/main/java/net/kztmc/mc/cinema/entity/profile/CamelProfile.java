package net.kztmc.mc.cinema.entity.profile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

/** ラクダ・ラクダハスク。アイドル・座る・座っている・立ち上がる・ダッシュ。 */
public final class CamelProfile extends AnimationModeProfile {
    public CamelProfile() {
        super("state", "動作",
                new String[] {"アイドル", "座る", "座っている", "立ち上がる", "ダッシュ"},
                new String[] {"idleAnimationState", "sitAnimationState", "sitPoseAnimationState",
                        "sitUpAnimationState", "dashAnimationState"},
                "minecraft:camel", "minecraft:camel_husk");
        // 歩行はキーフレーム。1周期は約1.5秒、animateWalk の係数は2.0(どちらも推測値)
        walkKeyframe(1.5f, 2.0f);
    }

    @Override
    protected void onMode(Entity e, int mode, float time) {
        // 座る系は、座った姿勢(体の高さも変わる)にする
        e.setPose(mode == 1 || mode == 2 ? Pose.SITTING : Pose.STANDING);
    }
}