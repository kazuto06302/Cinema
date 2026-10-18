package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.npc.SceneFreeze;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 止めている間、NPCの描画情報を作るときの補間値を固定する(フレームごとのアニメーションの揺れを消す)。 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    /** 固定した補間値で作り直す呼び出しの中で、再び差し替えないための印 */
    @Unique
    private static boolean cinema$pinning;

    @Inject(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("HEAD"),
            cancellable = true)
    private void cinema$pinPartialTick(T entity, float partialTick, CallbackInfoReturnable<S> cir) {
        if (cinema$pinning || !SceneFreeze.isFrozenNpc(entity)) return;

        cinema$pinning = true;
        try {
            EntityRenderer<T, S> self = (EntityRenderer<T, S>) (Object) this;
            cir.setReturnValue(self.createRenderState(entity, SceneFreeze.partialTickOf(entity)));
        } finally {
            cinema$pinning = false;
        }
    }
}