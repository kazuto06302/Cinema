package net.kztmc.mc.cinema.entity.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityFlags;
import net.kztmc.mc.cinema.entity.EntityOption;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.RegistryHolders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.Entity;

/**
 * 絵画。絵の種類を選べる(ゲームに登録された、すべての絵画)。位置の補正はオフ(HangingProfile)。
 * 種類は、ゲームを開いたあとにしか分からない(データで決まる)ので、選択肢は、画面を作るたびに、そのときの一覧から作る。
 */
public final class PaintingProfile extends HangingProfile {
    private static final String KEY = "variant";
    private static final String DEFAULT_VARIANT = "minecraft:kebab";

    public PaintingProfile() {
        super("minecraft:painting");
        hideStates(EntityFlags.FIRE, EntityFlags.GLOW);
    }

    /** 絵画の種類のID(名前空間つき)を、並べ替えた一覧 */
    private static List<String> variantIds() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).listElementIds()
                .map(key -> key.identifier().toString())
                .sorted()
                .toList();
    }

    private static int defaultIndex(List<String> ids) {
        return Math.max(0, ids.indexOf(DEFAULT_VARIANT));
    }

    /** 表示名: 翻訳キー painting.<名前空間>.<名前>.title があれば、それ。なければ、ID */
    private static String displayName(String id) {
        int colon = id.indexOf(':');
        String key = "painting." + id.substring(0, colon) + "." + id.substring(colon + 1) + ".title";
        return Language.getInstance().has(key) ? I18n.get(key) : id.substring(colon + 1);
    }

    @Override
    public List<EntityOption> options() {
        List<EntityOption> list = new ArrayList<>(super.options());
        List<String> ids = variantIds();
        if (!ids.isEmpty()) {
            list.add(new EntityOption(KEY, "絵", 0, ids.size() - 1, defaultIndex(ids),
                    ids.stream().map(PaintingProfile::displayName).toList(), EntityOption.Kind.DROPDOWN));
        }
        return list;
    }

    @Override
    public void applyAnim(Entity e, float time, Map<String, Integer> options) {
        List<String> ids = variantIds();
        if (ids.isEmpty()) return;
        int index = Math.max(0, Math.min(ids.size() - 1, options.getOrDefault(KEY, defaultIndex(ids))));
        Holder<?> variant = RegistryHolders.get(e, Registries.PAINTING_VARIANT, ids.get(index));
        if (variant != null) EntityReflect.invokeFirst(e, List.of("setVariant"), variant);
    }

    @Override
    protected double[] sizeOf(Entity e) {
        // 絵の大きさ(ブロック)は、種類が持っている
        Object variant = EntityReflect.call(EntityReflect.call(e, "getVariant"), "value");
        double width = variant != null && EntityReflect.call(variant, "width") instanceof Number w ? w.doubleValue() : 1.0;
        double height = variant != null && EntityReflect.call(variant, "height") instanceof Number h ? h.doubleValue() : 1.0;
        return new double[] {width, height};
    }
}