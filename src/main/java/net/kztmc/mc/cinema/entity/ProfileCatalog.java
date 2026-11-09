package net.kztmc.mc.cinema.entity;

import net.kztmc.mc.cinema.entity.profile.*;

/** 挙動(アニメーション・固有オプションを持つクラス)の登録。フラグ(○×)は、entities.csv に書く */
final class ProfileCatalog {
    private ProfileCatalog() {}

    static void registerAll() {
        animals();
        aquatic();
        monsters();
        golems();
        objects();
        hanging();
        humans();
        tuning();
    }

    private static void animals() {
        reg(new ChickenProfile());
        reg(new ParrotProfile());
        reg(new BatProfile());
        reg(new FrogProfile());
        reg(new SnifferProfile());
        reg(new ArmadilloProfile());
        reg(new CamelProfile());
        reg(new FoxProfile());
        reg(new MooshroomProfile());
        reg(new PolarBearProfile());
        reg(new StriderProfile());
        reg(new WolfProfile());
        reg(new CatProfile());
        reg(new BeeProfile());
        reg(new HorseProfile());
        reg(new LlamaProfile());
        reg(new PandaProfile());
        reg(new SheepProfile());
    }

    private static void aquatic() {
        reg(new GuardianProfile());
        reg(new SquidProfile());
        reg(new FishProfile());
        reg(new PufferfishProfile());
        reg(new DolphinProfile());
        reg(new TurtleProfile());
        reg(new AxolotlProfile());
    }

    private static void monsters() {
        reg(new CreeperProfile());
        reg(new ShulkerProfile());
        reg(new SlimeProfile());
        reg(new EnderDragonProfile());
        reg(new PhantomProfile());
        reg(new EvokerProfile());
        reg(new EvokerFangsProfile());
        reg(new HoglinProfile());
        reg(new WardenProfile());
        reg(new WitherProfile());
        reg(new WitherSkullProfile());
        reg(new BreezeProfile());
        reg(new GhastProfile("minecraft:ghast", true));
        reg(new GhastProfile("minecraft:happy_ghast", false));
    }

    private static void golems() {
        reg(new IronGolemProfile());
        reg(new SnowGolemProfile());
        reg(new CopperGolemProfile());
    }

    private static void objects() {
        reg(new EndCrystalProfile());
        reg(new CushionProfile());
        reg(new FallingBlockProfile());
    }

    private static void hanging() {
        reg(new ItemFrameProfile());
        reg(new PaintingProfile());
    }

    private static void humans() {
        reg(new PlayerProfile());
    }

    /** 数値の調整(devモードで合わせた値)。フラグではないので、表ではなくコードに置く */
    private static void tuning() {
        // クリーキング: 歩行はキーフレーム。周期2秒・係数7.0・振れ幅0.6は、仮の値
        EntityProfiles.ensure("minecraft:creaking").walkKeyframe(2.0f, 7.0f, 0.6f);
        // ウォーデン: 歩行は、min(0.5, 3×強さ) で制限される。歩行スライダー100%を、その上限(強さ 1/6)に合わせる
        EntityProfiles.ensure("minecraft:warden").walkSpeedMax(1f / 6f);
    }

    private static void reg(EntityProfile profile) {
        EntityProfiles.register(profile);
    }
}