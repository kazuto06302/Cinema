package net.kztmc.mc.cinema.mixin.ui.toggle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class NameTagToggleMixin {
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void cinema$nameTag(EntityRenderState state, PoseStack pose, SubmitNodeCollector out,
                                CameraRenderState camera, CallbackInfo ci) {
        if (NpcManager.isNpcState(state)) return;
        if (!RenderToggles.isOn(RenderToggles.Item.NAME_TAGS)) ci.cancel();
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("HEAD"), cancellable = true)
    private void cinema$nameTag(EntityRenderState state, PoseStack pose, SubmitNodeCollector out,
                                CameraRenderState camera, int yOffset, CallbackInfo ci) {
        if (!RenderToggles.isOn(RenderToggles.Item.NAME_TAGS)) ci.cancel();
    }
}