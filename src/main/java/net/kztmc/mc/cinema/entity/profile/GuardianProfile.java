package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ガーディアンとエルダーガーディアン。角と尾は tickCount ではなく専用の状態で動くので、
 * バニラの更新式(水中で止まっているとき)を指定tick回だけ繰り返した結果を書き込む。
 */
public final class GuardianProfile extends EntityProfile {
    public GuardianProfile() {
        super("minecraft:guardian", "minecraft:elder_guardian");
        defaultAnim(20);   // 角が出た状態
    }

    @Override
    public void applyAnim(Entity e, int tick) {
        float spikesO = 0f, spikes = 0f;
        float tailO = 0f, tail = 0f, speed = 0f;
        for (int i = 0; i < Math.min(tick, 200); i++) {
            tailO = tail;
            speed += (0.125f - speed) * 0.2f;
            tail += speed;
            spikesO = spikes;
            spikes += (1.0f - spikes) * 0.06f;   // 大きくすると、角が早く出る
        }
        EntityReflect.setFloat(e, "clientSideSpikesAnimation", spikes);
        EntityReflect.setFloat(e, "clientSideSpikesAnimationO", spikesO);
        EntityReflect.setFloat(e, "clientSideTailAnimation", tail);
        EntityReflect.setFloat(e, "clientSideTailAnimationO", tailO);
        EntityReflect.setFloat(e, "clientSideTailAnimationSpeed", speed);
    }
}