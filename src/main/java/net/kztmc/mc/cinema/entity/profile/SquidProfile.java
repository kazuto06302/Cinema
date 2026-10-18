package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * イカとヒカリイカ。触手は tentacleMovement(0〜2π)が進むにつれて、
 * 水中では「開いてから閉じる」動きをする。バニラの式を、固定の速さで進めたものを書き込む。
 */
public final class SquidProfile extends EntityProfile {
    /** 1tickで進む位相(rad)。バニラは個体ごとに 0.1〜0.2 程度。大きいほど速く動く */
    private static final float TENTACLE_SPEED = 0.2f;

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    public SquidProfile() {
        super("minecraft:squid", "minecraft:glow_squid");
        defaultAnim(10);   // 触手が開いている位置
    }

    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        float oldMove = Math.min(TENTACLE_SPEED * n, TWO_PI);
        float move = Math.min(TENTACLE_SPEED * (n + 1), TWO_PI);

        EntityReflect.setFloat(e, "oldTentacleMovement", oldMove);
        EntityReflect.setFloat(e, "tentacleMovement", move);
        EntityReflect.setFloat(e, "oldTentacleAngle", angle(oldMove));
        EntityReflect.setFloat(e, "tentacleAngle", angle(move));
    }

    /** バニラの「水中」の式。前半(0〜π)で開いて、後半は閉じたまま */
    private static float angle(float move) {
        if (move >= (float) Math.PI) return 0f;
        float f = move / (float) Math.PI;
        return Mth.sin(f * f * (float) Math.PI) * (float) Math.PI * 0.25f;
    }
}