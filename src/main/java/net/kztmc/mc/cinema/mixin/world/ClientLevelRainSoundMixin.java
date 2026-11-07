package net.kztmc.mc.cinema.mixin.world;

import net.kztmc.mc.cinema.ui.panel.world.ClientOverrides;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 天気を「雨・雷雨」に上書きしている間は、雨の音を鳴らさない */
@Mixin(ClientLevel.class)
public abstract class ClientLevelRainSoundMixin {
    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
            at = @At("HEAD"), cancellable = true)
    private void cinema$muteRain(double x, double y, double z, SoundEvent sound, SoundSource source,
                                 float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        if (!ClientOverrides.weatherOn || ClientOverrides.weather < 1) return;
        if (source == SoundSource.WEATHER && sound.location().getPath().startsWith("weather.rain")) ci.cancel();
    }
}