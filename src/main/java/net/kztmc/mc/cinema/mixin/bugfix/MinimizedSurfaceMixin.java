package net.kztmc.mc.cinema.mixin.bugfix;

import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.device.SurfaceException;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MinimizedSurfaceMixin {

    @Redirect(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuSurface;acquireNextTexture()V"
            )
    )
    private void cinema$handleSurfaceException(GpuSurface surface)
            throws SurfaceException {

        Minecraft minecraft = (Minecraft) (Object) this;

        if (minecraft.getWindow().isIconified()) {
            return;
        }

        // 通常時はMinecraft本来の例外処理に任せる
        surface.acquireNextTexture();
    }
}