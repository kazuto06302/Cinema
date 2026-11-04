package net.kztmc.mc.cinema.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import net.kztmc.mc.cinema.ui.SelectionOutline;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSelectionMixin {
    @Shadow @Final private GameRenderer gameRenderer;
    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
    @Shadow @Final private ShaderManager shaderManager;

    @Unique private RenderTarget cinema$target;
    @Unique private SubmitNodeStorage cinema$storage;
    @Unique private boolean cinema$active;

    /** 選択NPCだけを、Cinema専用の保管所へ、もう1回 submit する */
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void cinema$submitSelected(PoseStack poseStack, LevelRenderState levelRenderState,
                                       SubmitNodeCollector output, CallbackInfo ci) {
        cinema$storage = null;
        List<EntityRenderState> selected = SelectionOutline.take();
        if (selected.isEmpty()) return;

        cinema$storage = new SubmitNodeStorage();
        cinema$storage.setUseImprovedTransparency(gameRenderer.useImprovedTransparency());
        Vec3 cam = levelRenderState.cameraRenderState.pos;
        for (EntityRenderState state : selected) {
            state.outlineColor = SelectionOutline.COLOR;   // こちらだけ、アウトラインの対象にする
            entityRenderDispatcher.submit(state, levelRenderState.cameraRenderState,
                    state.x - cam.x(), state.y - cam.y(), state.z - cam.z(), poseStack, cinema$storage);
        }
    }

    /**
     * バニラのフレーム(featureFrame)が閉じた後に、専用の小さなフレームグラフを実行する。
     * この時点なら、PreparedFrame も、共有の頂点バッファも、空いている。
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void cinema$drawOutline(GraphicsResourceAllocator allocator, boolean renderOutline,
                                    CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor,
                                    boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
        cinema$active = false;
        SubmitNodeStorage storage = cinema$storage;
        cinema$storage = null;
        if (storage == null) return;

        RenderTarget main = gameRenderer.mainRenderTarget();
        if (cinema$target == null) {
            cinema$target = new TextureTarget("Cinema Selection Outline", main.width, main.height,
                    GpuFormat.RGBA8_UNORM, null);
        } else if (cinema$target.width != main.width || cinema$target.height != main.height) {
            cinema$target.resize(main.width, main.height);
        }

        PostChain chain = shaderManager.getPostChain(SelectionOutline.CHAIN_ID, Set.of(SelectionOutline.TARGET_ID));
        if (chain == null) return;

        // バニラの render は、終わるときに、カメラの行列を戻している。描くあいだだけ、もう一度かける
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(cameraState.viewRotationMatrix);
        FeatureRenderDispatcher.PreparedFrame prepared = null;
        try {
            RenderSystem.setShaderFog(terrainFog);
            gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);

            prepared = featureRenderDispatcher.prepareFrame(storage);
            if (!prepared.hasAnyOutline()) return;
            final FeatureRenderDispatcher.PreparedFrame frameToDraw = prepared;
            final RenderTarget target = cinema$target;

            FrameGraphBuilder frame = new FrameGraphBuilder();
            ResourceHandle<RenderTarget> handle = frame.importExternal("cinema_selection_outline", target);
            FramePass pass = frame.addPass("cinema_outline");
            handle = pass.readsAndWrites(handle);
            pass.executes(() -> {
                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                        .createRenderPass(() -> "Cinema Outline", target.getColorTextureView(),
                                Optional.of((Vector4fc) new Vector4f(0.0F)), null, OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(renderPass);
                    frameToDraw.executeOutline(renderPass);
                }
            });
            chain.addToFrame(frame, main.width, main.height,
                    PostChain.TargetBundle.of(SelectionOutline.TARGET_ID, handle));
            frame.execute(allocator, new FrameGraphBuilder.Inspector() {
                @Override public void beforeExecutePass(String name) {}
                @Override public void afterExecutePass(String name) {}
            });
            cinema$active = true;
        } finally {
            if (prepared != null) prepared.close();
            modelViewStack.popMatrix();
        }
    }

    /** バニラの発光の合成の後ろで、メイン画面へ合成する */
    @Inject(method = "blitEntityOutline", at = @At("TAIL"))
    private void cinema$blit(CallbackInfo ci) {
        if (cinema$active && cinema$target != null) {
            cinema$target.blitAndBlendToTexture(gameRenderer.mainRenderTarget().getColorTextureView(),
                    gameRenderer.mainRenderTarget().getDepthTextureView());
        }
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void cinema$resize(int width, int height, CallbackInfo ci) {
        if (cinema$target != null) cinema$target.resize(width, height);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void cinema$close(CallbackInfo ci) {
        if (cinema$target != null) {
            cinema$target.destroyBuffers();
            cinema$target = null;
        }
    }
}