package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.NpcData;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * ぶら下がるもの(額縁・絵画)の共通部分。
 * バニラは、位置を、壁の向きとブロックの座標へ吸着させる。ここでは、その補正を使わず、指定した座標へ、そのまま置く。
 * 向きは、向きのスライダー(yRot)から、最も近い4方向。Y は、絵の下端の高さ。
 */
public abstract class HangingProfile extends EntityProfile {
    /** 絵の厚み(ブロック) */
    private static final double THICKNESS = 1.0 / 16.0;

    protected HangingProfile(String... ids) {
        super(ids);
        noHitbox();
        noWalk();
    }

    /** 絵の大きさ {幅, 高さ}(ブロック) */
    protected abstract double[] sizeOf(Entity e);

    @Override
    public void applyData(Entity e, NpcData d) {
        Direction facing = Direction.fromYRot(d.yRot());
        EntityReflect.invokeFirst(e, List.of("setDirection"), facing);

        double[] size = sizeOf(e);
        double width = size[0];
        double height = size[1];
        double cx = d.x();
        double cy = d.y() + height / 2.0;
        double cz = d.z();

        // 吸着させずに、そのまま置く
        e.setPosRaw(cx, cy, cz);
        // 厚みの向きは、向きの軸。幅は、その直角
        boolean alongZ = facing.getAxis() == Direction.Axis.Z;
        double hx = (alongZ ? width : THICKNESS) / 2.0;
        double hz = (alongZ ? THICKNESS : width) / 2.0;
        e.setBoundingBox(new AABB(cx - hx, cy - height / 2.0, cz - hz, cx + hx, cy + height / 2.0, cz + hz));

        e.xo = e.xOld = cx;
        e.yo = e.yOld = cy;
        e.zo = e.zOld = cz;
    }
}