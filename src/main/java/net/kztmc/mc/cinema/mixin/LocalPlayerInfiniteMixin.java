package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.panel.inventory.CinemaCreativeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class LocalPlayerInfiniteMixin {
    @Inject(method = "hasInfiniteMaterials", at = @At("HEAD"), cancellable = true)
    private void cinema$infinite(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().gui.screen() instanceof CinemaCreativeScreen) cir.setReturnValue(true);
    }
}