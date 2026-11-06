package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.panel.inventory.CinemaCreativeScreen;
import net.kztmc.mc.cinema.ui.panel.inventory.CinemaInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeInventoryMixin {
    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void cinema$noCreativePacket(ItemStack clicked, int slot, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof CinemaCreativeScreen) ci.cancel();
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void cinema$noClickPacket(int containerId, int slot, int button, ContainerInput type, Player player, CallbackInfo ci) {
        if (Minecraft.getInstance().gui.screen() instanceof CinemaCreativeScreen || Minecraft.getInstance().gui.screen() instanceof CinemaInventoryScreen)
            ci.cancel();
    }
}