package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossBarToggleMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void cinema$hide(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!RenderToggles.isOn(RenderToggles.Item.BOSS_BAR)) ci.cancel();
    }
}