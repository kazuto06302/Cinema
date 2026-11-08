package net.kztmc.mc.cinema.mixin.ui.toggle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.npc.SelectionOutline;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggleFilter;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
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

    /** ブロック(地形)の描画。オフなら、地形を描かない */
    @Redirect(method = {"executeSolid", "executeClassicTransparency"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    private void cinema$blocks(ChunkSectionsToRender sections, ChunkSectionLayerGroup group, RenderPass pass,
                               GpuSampler sampler, GpuTextureView atlas, boolean wireframe) {
        if (RenderToggles.isOn(RenderToggles.Item.BLOCKS)) sections.renderGroup(group, pass, sampler, atlas, wireframe);
    }

    /** ブロックエンティティ(チェスト・看板・ビーコンなど)。ブロックをオフにしたら、これも描かない */
    @Redirect(method = "submitBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void cinema$blockEntities(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state,
                                      PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (RenderToggles.isOn(RenderToggles.Item.BLOCKS)) dispatcher.submit(state, poseStack, collector, camera);
    }
}