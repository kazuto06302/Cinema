package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.EntityIds;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * エンダードラゴン。
 *  - 当たり判定の部位は、バニラが毎tick置き直しているので、NPC(ティックされない)では自分で配置する。
 *  - 翼の羽ばたきは flapTime で決まる。静止しているとき、バニラは1tickで0.2ずつ進める。
 */
public final class EnderDragonProfile extends EntityProfile {
    /** 羽ばたきの位相が1tickで進む量(バニラの、静止しているとき) */
    private static final float FLAP_PER_TICK = 0.2f;

    public EnderDragonProfile() {
        super("minecraft:ender_dragon");
        defaultAnim(0);
    }

    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        EntityReflect.setFloat(e, "oFlapTime", FLAP_PER_TICK * n);
        EntityReflect.setFloat(e, "flapTime", FLAP_PER_TICK * (n + 1));
    }

    /** バニラの aiStep の、水平に静止しているときの部位の配置。順序は head, neck, body, tail1〜3, wing1, wing2 */
    @Override
    public void applyTransform(Entity e) {
        Entity[] parts = EntityIds.subEntities(e);
        if (parts.length < 8) return;

        float yaw = e.getYRot() * ((float) Math.PI / 180f);
        float s = Mth.sin(yaw);
        float c = Mth.cos(yaw);

        place(e, parts[0], s * 6.5f, 0.0, -c * 6.5f);   // head
        place(e, parts[1], s * 5.5f, 0.0, -c * 5.5f);   // neck
        place(e, parts[2], s * 0.5f, 0.0, -c * 0.5f);   // body
        for (int k = 0; k < 3; k++) {                   // tail1〜3
            float d = 1.5f + (k + 1) * 2.0f;
            place(e, parts[3 + k], -s * d, 1.5, c * d);
        }
        place(e, parts[6], c * 4.5f, 2.0, s * 4.5f);    // wing1
        place(e, parts[7], -c * 4.5f, 2.0, -s * 4.5f);  // wing2
    }

    private static void place(Entity dragon, Entity part, double dx, double dy, double dz) {
        part.setPos(dragon.getX() + dx, dragon.getY() + dy, dragon.getZ() + dz);
        part.setOldPosAndRot();   // 前回位置も揃える(当たり判定の描画は、前回位置と補間される)
    }
}