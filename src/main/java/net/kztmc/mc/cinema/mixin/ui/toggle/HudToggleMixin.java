package net.kztmc.mc.cinema.mixin.ui.toggle;
import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudToggleMixin {
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void cinema$scoreboard(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        if (!RenderToggles.isOn(RenderToggles.Item.SCOREBOARD)) ci.cancel();
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void cinema$actionBar(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        if (!RenderToggles.isOn(RenderToggles.Item.ACTION_BAR)) ci.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void cinema$title(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        if (!RenderToggles.isOn(RenderToggles.Item.TITLE)) ci.cancel();
    }
}