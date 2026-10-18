package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * ガーディアンとエルダーガーディアン。角と尾は tickCount ではなく専用の状態で動くので、
 * バニラの更新式(水中で止まっているとき)を繰り返した結果を書き込む。
 */
public final class GuardianProfile extends EntityProfile {
    private static final class State {
        float spikes;
        float tail;
        float tailSpeed;

        void step() {
            tailSpeed += (0.125f - tailSpeed) * 0.2f;
            tail += tailSpeed;
            spikes += (1.0f - spikes) * 0.06f;   // 大きくすると、角が早く出る
        }
    }

    public GuardianProfile() {
        super("minecraft:guardian", "minecraft:elder_guardian");
        defaultAnim(20);   // 角が出た状態
    }

    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        State s = new State();
        for (int i = 0; i < Math.min(n, 400); i++) s.step();

        float spikesO = s.spikes;
        float tailO = s.tail;
        s.step();

        EntityReflect.setFloat(e, "clientSideSpikesAnimationO", spikesO);
        EntityReflect.setFloat(e, "clientSideSpikesAnimation", s.spikes);
        EntityReflect.setFloat(e, "clientSideTailAnimationO", tailO);
        EntityReflect.setFloat(e, "clientSideTailAnimation", s.tail);
        EntityReflect.setFloat(e, "clientSideTailAnimationSpeed", s.tailSpeed);
    }
}