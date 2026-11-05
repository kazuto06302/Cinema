package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.ClientOverrides;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void cinema$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (ClientOverrides.weatherOn && (Object) this instanceof ClientLevel) cir.setReturnValue(ClientOverrides.rain());
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void cinema$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (ClientOverrides.weatherOn && (Object) this instanceof ClientLevel) cir.setReturnValue(ClientOverrides.thunder());
    }
}