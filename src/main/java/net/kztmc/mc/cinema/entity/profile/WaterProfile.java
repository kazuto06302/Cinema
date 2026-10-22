package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * 水中かどうかで見た目が変わるモブの共通部分。
 * 魚は水の外では横倒しになり、水中では体を起こして泳ぐ姿になる。NPCは水の中にいないので、フラグで切り替える。
 * 魚のように常に水中のモブは、選択肢を出さず、常に水中にする。
 */
public abstract class WaterProfile extends EntityProfile {
    protected static final String WATER = "water";
    private final int waterDefault;
    private final boolean selectable;

    /**
     * @param waterDefault 水中にするか(選択できないモブは、常にこの値)
     * @param selectable   右パネルで切り替えられるようにするか(カメのように、陸と水中の両方が使うモブだけ true)
     */
    protected WaterProfile(boolean waterDefault, boolean selectable, String... ids) {
        super(ids);
        this.waterDefault = waterDefault ? 1 : 0;
        this.selectable = selectable;
        if (selectable) {
            choice(WATER, "水中", this.waterDefault, "いない", "いる");
        }
    }

    @Override
    public final void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int water = selectable ? options.getOrDefault(WATER, waterDefault) : waterDefault;
        EntityReflect.setBooleanFirst(e, water == 1, "wasTouchingWater", "touchingWater", "inWater");
        applyExtra(e, time, options);
    }

    /** 水中の設定のほかに、種類ごとの設定があれば、ここで反映する */
    protected void applyExtra(Entity e, float time, Map<String, Integer> options) {}
}