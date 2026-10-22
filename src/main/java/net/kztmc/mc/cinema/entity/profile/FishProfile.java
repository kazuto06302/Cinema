package net.kztmc.mc.cinema.entity.profile;

/** タラ・サケ・熱帯魚。水中にいるかどうかで、横倒しか泳ぐ姿かが変わる(初期値は水中)。 */
public final class FishProfile extends WaterProfile {
    public FishProfile() {
        super(true, false, "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish");
    }
}