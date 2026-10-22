package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * 水中かどうかで見た目が変わるモブの共通部分。
 * 魚は、水の外では横倒しになり、水中では体を起こして泳ぐ姿になる。NPCは水の中にいないので、フラグで切り替える。
 */
public abstract class WaterProfile extends EntityProfile {
    protected static final String WATER = "water";
    private final int waterDefault;

    protected WaterProfile(boolean waterDefault, String... ids) {
        super(ids);
        this.waterDefault = waterDefault ? 1 : 0;
        choice(WATER, "水中", this.waterDefault, "いない", "いる");
    }

    @Override
    public final void applyAnim(Entity e, float time, Map<String, Integer> options) {
        boolean inWater = options.getOrDefault(WATER, waterDefault) == 1;
        EntityReflect.setBooleanFirst(e, inWater, "wasTouchingWater", "touchingWater", "inWater");
        applyExtra(e, time, options);
    }

    /** 水中の設定のほかに、種類ごとの設定があれば、ここで反映する */
    protected void applyExtra(Entity e, float time, Map<String, Integer> options) {}
}