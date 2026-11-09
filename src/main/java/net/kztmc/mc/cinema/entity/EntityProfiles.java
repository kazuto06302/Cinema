package net.kztmc.mc.cinema.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kztmc.mc.cinema.entity.profile.BoatProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/**
 * エンティティIDから設定を引く登録簿。
 *   挙動(Java のクラス)  : ProfileCatalog に、種類ごとにまとめて登録する
 *   フラグ(歩く・装備欄など): entities.csv(EntityTable)から反映する
 */
public final class EntityProfiles {
    private EntityProfiles() {}

    // 注意: static の初期化は、書いた順に走る。下の static ブロックが使うものは、先に宣言する
    private static final Map<String, EntityProfile> BY_ID = new ConcurrentHashMap<>();
    private static final EntityProfile DEFAULT = EntityProfile.of();
    private static final EntityProfile BOAT = new BoatProfile();

    static {
        ProfileCatalog.registerAll();   // 挙動
        EntityTable.applyAll();         // フラグ(表)
    }

    public static void register(EntityProfile profile) {
        for (String id : profile.ids()) BY_ID.put(id, profile);
    }

    /** 登録が無いエンティティには、何も設定していない既定の設定が返る */
    public static EntityProfile of(String id) {
        EntityProfile p = BY_ID.get(id);
        if (p != null) return p;
        if (id.endsWith("_boat") || id.endsWith("_raft")) {   // チェスト付きも、この末尾で当たる
            BY_ID.put(id, BOAT);
            return BOAT;
        }
        return DEFAULT;
    }

    public static EntityProfile of(Entity e) {
        return of(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    /** あればそれを返し、なければ作って登録する(表や調整の設定を、後から足すとき用) */
    public static EntityProfile ensure(String id) {
        EntityProfile p = of(id);
        if (p == DEFAULT) {
            p = EntityProfile.of(id);
            register(p);
        }
        return p;
    }
}