package net.kztmc.mc.cinema.ui;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 修飾キー(Shift/Ctrl)の状態。マウスホイールやドラッグには、キーの情報が付かないので、ここで取る。
 * バージョンによって、メソッドの置き場所が違うので、Screen と Minecraft から、名前で探す(無ければ、押されていない扱い)。
 */
public final class Modifiers {
    private Modifiers() {}

    private static final Method SHIFT = find("hasShiftDown");
    private static final Method CTRL = find("hasControlDown");

    private static Method find(String name) {
        try {
            return Screen.class.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            // 次へ
        }
        try {
            return Minecraft.class.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean call(Method m) {
        if (m == null) return false;
        try {
            Object target = Modifier.isStatic(m.getModifiers()) ? null : Minecraft.getInstance();
            return Boolean.TRUE.equals(m.invoke(target));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    public static boolean shift() {
        return call(SHIFT);
    }

    public static boolean ctrl() {
        return call(CTRL);
    }

    /** 刻みの倍率: なし 1、Shift 10、Ctrl 100 */
    public static double factor() {
        return ctrl() ? 100.0 : shift() ? 10.0 : 1.0;
    }
}