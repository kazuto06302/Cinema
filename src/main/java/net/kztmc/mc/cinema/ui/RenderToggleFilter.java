package net.kztmc.mc.cinema.ui;

import net.kztmc.mc.cinema.npc.NpcManager;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class RenderToggleFilter {
    private RenderToggleFilter() {}

    public static boolean shouldRender(EntityRenderState state) {
        if (SelectionOutline.isCollected(state)) return true;   // 選択中のNPCは、常に描く
        if (NpcManager.isNpcState(state)) return true;          // NPC は、常に描く
        if (state instanceof AvatarRenderState) return RenderToggles.isOn(RenderToggles.Item.PLAYERS);
        return RenderToggles.isOn(RenderToggles.Item.ENTITIES);
    }
}