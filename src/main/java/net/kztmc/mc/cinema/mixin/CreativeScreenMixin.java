package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.CinemaCreativeScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Inject(method = "containerTick", at = @At("HEAD"), cancellable = true)
    private void cinema$preventInventorySwitch(CallbackInfo ci) {
        if ((Object) this instanceof CinemaCreativeScreen) ci.cancel();
    }
}