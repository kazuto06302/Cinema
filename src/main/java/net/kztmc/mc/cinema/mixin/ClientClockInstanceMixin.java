package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.ClientOverrides;
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
        if (!ClientOverrides.timeOn) return;

        // 日数を維持して、1日の中の時刻だけ変更
        long dayStart = realTime - Math.floorMod(realTime, 24000L);
        long overriddenTime = dayStart + Math.floorMod(ClientOverrides.timeTicks, 24000);

        cir.setReturnValue(overriddenTime);
    }

    @Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
    private void cinema$freezePartialTick(CallbackInfoReturnable<Float> cir) {
        if (ClientOverrides.timeOn) cir.setReturnValue(0.0F);
    }
}