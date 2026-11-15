package net.kztmc.mc.cinema.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.kztmc.mc.cinema.entity.PartEdits;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
public abstract class ModelPartEditMixin {
    // 姿勢を作る直前: どの NPC の描画か(編集があるか)を、覚える
    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void cinema$begin(Object state, CallbackInfo ci) {
        PartEdits.beginModel(state);
    }

    // 描く直前: 覚えた編集を、パーツに足す
    @Inject(method = "renderToBuffer", at = @At("HEAD"))
    private void cinema$apply(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        PartEdits.applyCurrent((Model) (Object) this);
    }

    // 描き終わったら、忘れる(別のモデルに、編集が移らないように)
    @Inject(method = "renderToBuffer", at = @At("RETURN"))
    private void cinema$end(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        PartEdits.endModel();
    }
}