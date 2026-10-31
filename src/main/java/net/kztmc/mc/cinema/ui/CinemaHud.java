package net.kztmc.mc.cinema.ui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** HUDの調整。Cinemaの画面を開いている間は、クロスヘアを描かない。 */
public final class CinemaHud {
    private CinemaHud() {}

    public static void init() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original ->
                (HudElement) Proxy.newProxyInstance(HudElement.class.getClassLoader(), new Class<?>[] {HudElement.class},
                        (proxy, method, args) -> {
                            // 戻り値のないメソッドは、描画の呼び出し。Cinemaの画面を開いている間は、描かない
                            if (method.getReturnType() == void.class && cinemaScreenOpen()) return null;
                            try {
                                return method.invoke(original, args);
                            } catch (InvocationTargetException ex) {
                                throw ex.getCause();
                            }
                        }));
    }

    private static boolean cinemaScreenOpen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof CinemaScreen
                || screen instanceof CinemaInventoryScreen
                || screen instanceof CinemaCreativeScreen
                || screen instanceof EquipmentMenuScreen;
    }
}