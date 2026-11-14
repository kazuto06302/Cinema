package net.kztmc.mc.cinema.entity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

// NPC の全体の回転(X 軸・Z 軸まわり)。Y 軸(体の向き)は、ゲーム標準の向きを使う
public final class EntityRotation {
    private EntityRotation() {}

    // 保存するときのキー(NPC のオプション)。値は、度の100倍の整数
    public static final String X = "rotx";
    public static final String Z = "rotz";

    // 描画の情報(EntityRenderState)に持たせる回転 { X軸まわり, Z軸まわり }(ラジアン)
    public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "cinema:rotation");

    private static final Map<Entity, float[]> BY_ENTITY = Collections.synchronizedMap(new WeakHashMap<>());

    // NPC を作り直したときに呼ぶ(度の100倍の値)
    public static void set(Entity entity, int xCenti, int zCenti) {
        if (xCenti == 0 && zCenti == 0) {
            BY_ENTITY.remove(entity);
            return;
        }
        BY_ENTITY.put(entity, new float[] {
                (float) Math.toRadians(xCenti / 100.0), (float) Math.toRadians(zCenti / 100.0)});
    }

    // 描画の情報を作った直後に呼ぶ(回転があれば、描画の情報に持たせる)
    public static void attach(Entity entity, EntityRenderState state) {
        float[] rotation = BY_ENTITY.get(entity);
        if (rotation != null) state.setData(KEY, rotation);
    }

    public static float[] of(EntityRenderState state) {
        return state.getData(KEY);
    }
}