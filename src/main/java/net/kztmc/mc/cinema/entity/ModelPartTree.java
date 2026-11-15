package net.kztmc.mc.cinema.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.Entity;

// NPC のモデルから、編集できるパーツの一覧(木)を作る
public final class ModelPartTree {
    private ModelPartTree() {}

    // 1つのパーツ(path は「body/right_arm」の形、depth は木の深さ)
    public record Node(String path, String name, int depth) {}

    // 見やすい並び順(ここに無いものは、名前順で、後ろ)
    private static final List<String> ORDER = List.of("head", "hat", "body", "jacket", "right_arm", "right_sleeve",
            "left_arm", "left_sleeve", "right_leg", "right_pants", "left_leg", "left_pants", "cloak");

    public static List<Node> of(Entity entity) {
        List<Node> out = new ArrayList<>();
        ModelPart root = rootOf(entity);
        if (root != null) walk(root, "", 0, out);
        return out;
    }

    // この NPC を描くレンダラーが持つモデルの、根
    public static ModelPart rootOf(Entity entity) {
        Object renderer = Minecraft.getInstance().levelRenderer.entityRenderDispatcher().getRenderer(entity);
        Model model = findModel(renderer);
        return model == null ? null : model.root();
    }

    // レンダラーのフィールドから、モデルを探す("model" という名前を優先)
    private static Model findModel(Object renderer) {
        Model first = null;
        for (Class<?> c = renderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Model.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(renderer) instanceof Model m) {
                        if (f.getName().equals("model")) return m;
                        if (first == null) first = m;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 次のフィールドへ
                }
            }
        }
        return first;
    }

    private static void walk(ModelPart part, String prefix, int depth, List<Node> out) {
        if (!(EntityReflect.getObjectQuiet(part, "children") instanceof Map<?, ?> children)) return;
        List<Map.Entry<?, ?>> entries = new ArrayList<>(children.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<?, ?> e) -> rank(String.valueOf(e.getKey())))
                .thenComparing(e -> String.valueOf(e.getKey())));
        for (Map.Entry<?, ?> e : entries) {
            String name = String.valueOf(e.getKey());
            String path = prefix.isEmpty() ? name : prefix + "/" + name;
            out.add(new Node(path, name, depth));
            if (e.getValue() instanceof ModelPart child) walk(child, path, depth + 1, out);
        }
    }

    private static int rank(String name) {
        int i = ORDER.indexOf(name);
        return i < 0 ? ORDER.size() : i;
    }

    // 画面に出す名前(言語ファイルに cinema.part.<名前> があれば、それ。なければ、元の名前)
    public static String label(String name) {
        String key = "cinema.part." + name;
        return Language.getInstance().has(key) ? I18n.get(key) : name;
    }

    // パスの、最後の名前
    public static String lastName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}