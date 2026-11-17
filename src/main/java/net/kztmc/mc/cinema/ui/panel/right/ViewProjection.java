package net.kztmc.mc.cinema.ui.panel.right;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// 世界の点 → GUI の座標。軸の矢印を、マウスで掴むための計算
public final class ViewProjection {
    private ViewProjection() {}

    // 戻り値は { x, y }。カメラの後ろにあるときは null
    public static double[] toScreen(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cam = camera.position();

        // カメラから見た座標にする(前は -Z、右は +X、上は +Y)
        Vector3f v = new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
        camera.rotation().conjugate(new Quaternionf()).transform(v);
        if (v.z >= -0.05f) return null;

        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);   // 縦の視野角
        double aspect = (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
        double ndcX = v.x / (-v.z) / (tan * aspect);
        double ndcY = v.y / (-v.z) / tan;
        return new double[] {
                (ndcX * 0.5 + 0.5) * mc.getWindow().getGuiScaledWidth(),
                (0.5 - ndcY * 0.5) * mc.getWindow().getGuiScaledHeight()};
    }
}