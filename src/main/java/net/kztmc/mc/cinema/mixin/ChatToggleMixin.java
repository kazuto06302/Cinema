package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.ui.panel.world.RenderToggles;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatToggleMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void cinema$hide(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                             ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
        // チャット欄を開いているとき(入力中)は、消さない
        if (mode.foreground) return;
        if (!RenderToggles.isOn(RenderToggles.Item.CHAT)) ci.cancel();
    }
}