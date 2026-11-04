package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kztmc.mc.cinema.npc.EntityIds;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** 選択中のNPCの輪郭。バニラの発光とは別の経路(専用ターゲット)で描く */
public final class SelectionOutline {
    private SelectionOutline() {}

    public static final int COLOR = 0xFF4C9AFF;   // ARGB
    public static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath("cinema", "selection_outline");
    public static final Identifier TARGET_ID = Identifier.fromNamespaceAndPath("cinema", "selection_outline");

    private static final List<EntityRenderState> STATES = new ArrayList<>();

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

    /** 描画状態を作った直後に呼ぶ(EntityRendererMixin から) */
    public static void collect(EntityRenderState state) {
        if (STATES.size() < 16) STATES.add(state);
    }

    /** そのフレームで集めた、選択NPCの描画状態を渡して、空にする */
    public static List<EntityRenderState> take() {
        List<EntityRenderState> out = new ArrayList<>(STATES);
        STATES.clear();
        return out;
    }
}