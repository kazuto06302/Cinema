package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import net.kztmc.mc.cinema.entity.EntityFlags;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.NpcData;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * ぶら下がるもの(額縁・絵画)の共通部分。
 * バニラは、位置を、壁の向きとブロックの座標へ吸着させる。ここでは、その補正を使わず、指定した座標へ、そのまま置く。
 * 向きは、向きのスライダー(yRot)から、最も近い4方向。allowsVertical なら、頭の上下(xRot)で、上向き・下向きにもなる。
 * Y は、絵の下端の高さ(上向き・下向きのときは、板の中心の高さ)。Glow だけを残して、Fire は出さない。
 *
 * 注意: 額縁は、中身や回転を変えると、位置と当たり判定を、吸着し直す。そのため、中身と回転を先に入れて、
 * 位置は、最後に置く(applyData の中で、位置を置く placeFreely を、最後に呼ぶ)。
 */
public abstract class HangingProfile extends EntityProfile {
    /** 絵の厚み(ブロック) */
    private static final double THICKNESS = 1.0 / 16.0;

    protected HangingProfile(String... ids) {
        super(ids);
        noHitbox();
        noWalk();
        hideStates(EntityFlags.FIRE);
    }

    /** 絵の大きさ {幅, 高さ}(ブロック) */
    protected abstract double[] sizeOf(Entity e);

    /** 上向き・下向きにもできるか(額縁だけ) */
    protected boolean allowsVertical() {
        return false;
    }

    @Override
    public void applyData(Entity e, NpcData d) {
        placeFreely(e, d);
    }

    /** 向きを決めて、吸着させずに、指定した座標へ置く。中身の変更など、吸着し直す処理のあとに呼ぶこと */
    protected final void placeFreely(Entity e, NpcData d) {
        Direction facing = facingOf(d);
        EntityReflect.invokeFirst(e, List.of("setDirection"), facing);   // 吸着し直すので、このあとで置く

        double[] size = sizeOf(e);
        double width = size[0];
        double height = size[1];
        double cx = d.x();
        double cz = d.z();
        double cy;
        double hx;
        double hy;
        double hz;
        if (facing.getAxis() == Direction.Axis.Y) {
            // 上向き・下向き: 板は水平で、厚みが縦
            cy = d.y();
            hx = width / 2.0;
            hz = height / 2.0;
            hy = THICKNESS / 2.0;
        } else {
            cy = d.y() + height / 2.0;
            hy = height / 2.0;
            boolean alongZ = facing.getAxis() == Direction.Axis.Z;   // 南北向きなら、厚みが Z、幅が X
            hx = (alongZ ? width : THICKNESS) / 2.0;
            hz = (alongZ ? THICKNESS : width) / 2.0;
        }

        e.setPosRaw(cx, cy, cz);
        e.setBoundingBox(new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz));
        e.xo = e.xOld = cx;
        e.yo = e.yOld = cy;
        e.zo = e.zOld = cz;
    }

    private Direction facingOf(NpcData d) {
        if (allowsVertical()) {
            if (d.xRot() <= -45f) return Direction.UP;
            if (d.xRot() >= 45f) return Direction.DOWN;
        }
        return Direction.fromYRot(d.yRot());
    }
}