package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.RegistryHolders;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;

/**
 * 村人・ゾンビ村人。職業・種類(バイオーム)・レベルを選べる。
 * 村人のデータ(VillagerData)は、この3つで1組なので、いまの値に、順に差し替えて書き戻す。
 * クラスの場所が版で違うので、VillagerData は、リフレクションで扱う。
 */
public final class VillagerProfile extends EntityProfile {
    private static final String[] PROFESSIONS = {"none", "armorer", "butcher", "cartographer", "cleric", "farmer",
            "fisherman", "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith",
            "weaponsmith"};
    private static final String[] TYPES = {"desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga"};

    public VillagerProfile() {
        super("minecraft:villager", "minecraft:zombie_villager");
        dropdownChoice("profession", "職業", 0, "無職", "武具鍛冶", "肉屋", "製図家", "聖職者", "農民", "漁師", "矢師",
                "革細工師", "司書", "石工", "ニットウィット(緑の服)", "羊飼い", "道具鍛冶", "武器鍛冶");
        dropdownChoice("biome", "種類", 2, "砂漠", "ジャングル", "平原", "サバンナ", "雪原", "沼地", "タイガ");
        choice("level", "レベル", 0, "見習い", "下働き", "熟練", "エキスパート", "マスター");
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        int p = Math.max(0, Math.min(PROFESSIONS.length - 1, options.getOrDefault("profession", 0)));
        int t = Math.max(0, Math.min(TYPES.length - 1, options.getOrDefault("biome", 2)));
        int level = Math.max(0, Math.min(4, options.getOrDefault("level", 0))) + 1;   // 1〜5

        Holder<?> profession = RegistryHolders.get(e, Registries.VILLAGER_PROFESSION, PROFESSIONS[p]);
        Holder<?> type = RegistryHolders.get(e, Registries.VILLAGER_TYPE, TYPES[t]);
        Object data = EntityReflect.call(e, "getVillagerData");
        if (profession == null || type == null || data == null) return;

        data = with(data, "withProfession", profession);
        data = with(data, "withType", type);
        data = with(data, "withLevel", level);
        EntityReflect.invokeFirst(e, List.of("setVillagerData"), data);
    }

    /** VillagerData の with○○ を呼ぶ。呼べなければ、元のまま */
    private static Object with(Object data, String method, Object value) {
        Object next = EntityReflect.call(data, method, value);
        return next != null ? next : data;
    }
}