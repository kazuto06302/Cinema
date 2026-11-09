package net.kztmc.mc.cinema.mixin.ui.title;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.ui.core.Tr;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    /** バニラの「Minecraft 26.3」(いちばん下の行)より上に、何行ぶん空けるか。Fabric などの行が重なるなら、増やす */
    private static final int LINES_BELOW = 1;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cinema$installed(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        float alpha = fadeAlpha();
        int a = Mth.ceil(alpha * 255.0F);
        if (a < 4) return;   // バニラも、ほぼ透明のときは描かない

        int color = (Theme.ACCENT & 0xFFFFFF) | (a << 24);
        int y = g.guiHeight() - 10 * (LINES_BELOW + 1);
        g.text(Minecraft.getInstance().font, Tr.get("cinema.title.installed"), 2, y, color);
    }

    /** バニラの文字と同じ、フェードインの透明度(0〜1)。表示から1秒待って、次の1秒で現れる */
    private float fadeAlpha() {
        Object fading = EntityReflect.getObjectQuiet(this, "fading");
        Object start = EntityReflect.getObjectQuiet(this, "fadeInStart");
        if (!(fading instanceof Boolean f) || !(start instanceof Long s)) return 1.0F;   // 読めなければ、フェードなし
        if (!f) return 1.0F;
        float seconds = (Util.getMillis() - s) / 1000.0F;
        return Mth.clamp(seconds - 1.0F, 0.0F, 1.0F);
    }
}