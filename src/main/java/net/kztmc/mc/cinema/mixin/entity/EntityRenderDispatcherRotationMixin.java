package net.kztmc.mc.cinema.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kztmc.mc.cinema.entity.EntityRotation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherRotationMixin {
    private static final String RENDER = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    // モデルを描く前: 体の中心を軸にして、X・Z まわりに回す
    @Inject(method = "submit", at = @At(value = "INVOKE", target = RENDER))
    private void cinema$rotateBefore(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                     PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        float[] rotation = EntityRotation.of(state);
        if (rotation == null) return;
        float pivot = state.boundingBoxHeight * 0.5f;   // 体の高さの半分(中心)
        poseStack.pushPose();
        poseStack.translate(0.0, pivot, 0.0);
        poseStack.rotate(new Quaternionf().rotateX(rotation[0]).rotateZ(rotation[1]));
        poseStack.translate(0.0, -pivot, 0.0);
    }

    // モデルを描いた後: 元に戻す
    @Inject(method = "submit", at = @At(value = "INVOKE", target = RENDER, shift = At.Shift.AFTER))
    private void cinema$rotateAfter(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                    PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        if (EntityRotation.of(state) != null) poseStack.popPose();
    }
}