package net.kztmc.mc.cinema.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.npc.SelectionOutline;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggleFilter;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererToggleMixin {
    @Redirect(method = "submitEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private <S extends EntityRenderState> void cinema$filter(EntityRenderDispatcher d, S state, CameraRenderState cam, double x, double y, double z, PoseStack ps, SubmitNodeCollector out) {
        if (!RenderToggleFilter.shouldRender(state)) return;
        d.submit(state, cam, x, y, z, ps, out);
    }

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void cinema$clearStates(PoseStack ps, LevelRenderState s, SubmitNodeCollector out, CallbackInfo ci) {
        NpcManager.clearStates();
        SelectionOutline.endFrame();
    }

    @Redirect(method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void cinema$particles(ParticlesRenderState state, SubmitNodeCollector out, CameraRenderState cam) {
        if (RenderToggles.isOn(RenderToggles.Item.PARTICLES)) state.submit(out, cam);
    }
}