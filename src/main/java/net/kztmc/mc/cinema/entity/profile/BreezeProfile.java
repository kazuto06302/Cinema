package net.kztmc.mc.cinema.entity.profile;

/** ブリーズ。アイドル・滑空・滑空から戻る・ロングジャンプ・発射・息を吸う。時間スライダーで動かす。 */
public final class BreezeProfile extends AnimationModeProfile {
    public BreezeProfile() {
        super("state", "動作",
                new String[] {"アイドル", "滑空", "滑空から戻る", "ロングジャンプ", "発射", "息を吸う"},
                new String[] {"idle", "slide", "slideBack", "longJump", "shoot", "inhale"},
                "minecraft:breeze");
    }
}