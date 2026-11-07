package net.kztmc.mc.cinema.mixin.world;

import net.kztmc.mc.cinema.ui.panel.world.TextureAnimations;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** テクスチャ固定中は、雨・雪のスクロールに使う tick と補間値を固定する */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    private static final String RAIN = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;createRainColumnInstance(Lnet/minecraft/util/RandomSource;JIIIIIF)Lnet/minecraft/client/renderer/WeatherEffectRenderer$ColumnInstance;";
    private static final String SNOW = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;createSnowColumnInstance(Lnet/minecraft/util/RandomSource;JIIIIIF)Lnet/minecraft/client/renderer/WeatherEffectRenderer$ColumnInstance;";

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = RAIN), index = 1)
    private long cinema$rainTicks(long ticks) {
        return TextureAnimations.isFixed() ? TextureAnimations.fixedTick() : ticks;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = RAIN), index = 7)
    private float cinema$rainPartial(float partial) {
        return TextureAnimations.isFixed() ? 0.0F : partial;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = SNOW), index = 1)
    private long cinema$snowTicks(long ticks) {
        return TextureAnimations.isFixed() ? TextureAnimations.fixedTick() : ticks;
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = SNOW), index = 7)
    private float cinema$snowPartial(float partial) {
        return TextureAnimations.isFixed() ? 0.0F : partial;
    }
}