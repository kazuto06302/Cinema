package net.kztmc.mc.cinema.mixin.world;

import net.kztmc.mc.cinema.ui.panel.world.ClientOverrides;
import net.minecraft.client.ClientClockManager.ClientClockInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockInstance.class)
public abstract class ClientClockInstanceMixin {
    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void cinema$overrideTime(CallbackInfoReturnable<Long> cir) {
        long realTime = cir.getReturnValue();
        ClientOverrides.realTime = realTime;
        if (!ClientOverrides.timeOn && !ClientOverrides.moonOn) return;

        long day = Math.floorDiv(realTime, 24000L);
        long tick = ClientOverrides.timeOn
                ? Math.floorMod(ClientOverrides.timeTicks, 24000)
                : Math.floorMod(realTime, 24000L);
        if (ClientOverrides.moonOn) day += ClientOverrides.moonPhase - Math.floorMod(day, 8L);

        cir.setReturnValue(day * 24000L + tick);
    }

    @Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
    private void cinema$freezePartialTick(CallbackInfoReturnable<Float> cir) {
        if (ClientOverrides.timeOn) cir.setReturnValue(0.0F);
    }
}