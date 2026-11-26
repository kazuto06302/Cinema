package net.kztmc.mc.cinema.mixin.world;

import net.kztmc.mc.cinema.ui.panel.world.TextureAnimations;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** テクスチャ固定中は、パーティクルの動き(tick)と、描画の補間を止める */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineFreezeMixin {
    /** 固定中に、新しいパーティクルを出すか(false なら出さない。固定した瞬間の状態が、そのまま残る) */
    private static final boolean BLOCK_NEW = true;

    /** 位置・寿命の更新を止める。寿命が進まないので、消えもしない */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cinema$freezeTick(CallbackInfo ci) {
        if (TextureAnimations.isFixed()) ci.cancel();
    }

    /** 止めている間に出たパーティクルが、出た場所に溜まっていくのを防ぐ */
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cinema$blockNew(Particle particle, CallbackInfo ci) {
        if (BLOCK_NEW && TextureAnimations.isFixed()) ci.cancel();
    }

    /** 描画の補間値(1tick の中の位置)を固定する。これが無いと、止めた1tickの中で行き来して、揺れて見える */
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, require = 0)
    private float cinema$pinPartialTick(float partialTick) {
        return TextureAnimations.isFixed() ? 0.0F : partialTick;
    }
}