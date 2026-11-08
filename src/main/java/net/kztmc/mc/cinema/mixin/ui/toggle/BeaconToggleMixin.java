package net.kztmc.mc.cinema.mixin.ui.toggle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
public abstract class BeaconToggleMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BeaconRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void cinema$beam(BeaconRenderState state, PoseStack pose, SubmitNodeCollector out,
                             CameraRenderState camera, CallbackInfo ci) {
        if (!RenderToggles.isOn(RenderToggles.Item.BEACON_BEAMS)) ci.cancel();
    }
}