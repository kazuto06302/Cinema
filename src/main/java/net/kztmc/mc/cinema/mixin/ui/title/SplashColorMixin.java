package net.kztmc.mc.cinema.mixin.ui.title;

import net.kztmc.mc.cinema.ui.panel.Theme;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SplashRenderer.class)
public abstract class SplashColorMixin {
    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/network/chat/Component;)V"), index = 4)
    private Component cinema$splashColor(Component component) {
        return component.copy().withStyle(style -> style.withColor(Theme.ACCENT));
    }
}