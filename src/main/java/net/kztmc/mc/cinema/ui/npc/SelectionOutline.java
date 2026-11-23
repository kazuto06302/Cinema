package net.kztmc.mc.cinema.ui.npc;

import java.util.*;
import net.kztmc.mc.cinema.npc.EntityIds;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.CinemaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** 選択中のNPCの輪郭。バニラの発光とは別の経路(専用ターゲット)で描く */
public final class SelectionOutline {
    private SelectionOutline() {}

    public static final int COLOR = 0xFF4C9AFF;   // ARGB
    public static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath("cinema", "selection_outline");
    public static final Identifier TARGET_ID = Identifier.fromNamespaceAndPath("cinema", "selection_outline");
    private static final Set<EntityRenderState> SELECTED_THIS_FRAME = Collections.newSetFromMap(new IdentityHashMap<>());

    // 選択中の NPC の、エンティティと描画の情報
    public record Collected(Entity entity, EntityRenderState state) {}
    private static final List<Collected> STATES = new ArrayList<>();   // 前回の List<EntityRenderState> を置き換える
    // パーツを光らせるために、描画の情報を作り直している間は true(その間は、選択として集めない)
    public static boolean extractingGlow;

    public static boolean isSelected(Entity entity) {
        if (!(Minecraft.getInstance().gui.screen() instanceof CinemaScreen)) return false;
        UUID id = NpcManager.selectedId();
        if (id == null) return false;
        Entity selected = NpcManager.entityOf(id);
        if (selected == null) return false;
        if (entity == selected) return true;
        for (Entity part : EntityIds.subEntities(selected)) {
            if (part == entity) return true;
        }
        return false;
    }

    // 描画の情報を作った直後に呼ぶ(EntityRendererMixin から)
    public static void collect(Entity entity, EntityRenderState state) {
        if (extractingGlow) return;
        if (STATES.size() < 16) STATES.add(new Collected(entity, state));
        SELECTED_THIS_FRAME.add(state);
    }

    // そのフレームで集めた、選択中の NPC を渡して、空にする
    public static List<Collected> take() {
        List<Collected> out = new ArrayList<>(STATES);
        STATES.clear();
        return out;
    }

    // 輪郭の描画専用に、描画の情報をもう1つ作る
    public static EntityRenderState extractFresh(EntityRenderDispatcher dispatcher, Entity entity, float partialTick) {
        extractingGlow = true;
        try {
            return dispatcher.extractEntity(entity, partialTick);
        } finally {
            extractingGlow = false;
        }
    }

    /** 選択中のNPCの描画状態か(エンティティの描画除外フィルターで使う) */
    public static boolean isCollected(EntityRenderState state) {
        return SELECTED_THIS_FRAME.contains(state);
    }

    /** フレームの終わりに呼ぶ(古い状態が残らないように) */
    public static void endFrame() {
        SELECTED_THIS_FRAME.clear();
    }
}