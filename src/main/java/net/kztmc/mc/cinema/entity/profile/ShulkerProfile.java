package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.world.entity.Entity;

/**
 * シュルカー。殻の開き具合は currentPeekAmount(0〜1)。バニラは1tickで0.05ずつ目標へ近づく。
 * 0〜20tickで開いて、20〜40tickで閉じる動きにしている。
 */
public final class ShulkerProfile extends EntityProfile {
    private static final float STEP = 0.05f;
    private static final int OPEN_TICKS = 20;

    /**
     * 殻が開く量の上限(0〜1)。1.0だと、ふたが1ブロック分せり上がって大きく動く。
     * バニラの待機中の開き方は 0.3 程度。大きく動きすぎると感じたら下げる。
     */
    private static final float MAX_PEEK = 1.0f;

    public ShulkerProfile() {
        super("minecraft:shulker");
        defaultAnim(0);   // 全開
    }

    @Override
    public void applyAnim(Entity e, float time) {
        int n = (int) Math.floor(time);
        EntityReflect.setFloat(e, "currentPeekAmountO", peek(n));
        EntityReflect.setFloat(e, "currentPeekAmount", peek(n + 1));

        // ブロックをまたいで移動すると、移動の補間用のカウンターが6になり、ティックされないと減らないまま、
        // 古い位置に描画され続ける。0にして、補間を無効にする
        EntityReflect.setInt(e, "clientSideTeleportInterpolation", 0);
    }

    /** 0〜OPEN_TICKS で開き、OPEN_TICKS〜2倍で閉じる */
    private static float peek(int tick) {
        int t = Math.max(0, Math.min(tick, OPEN_TICKS * 2));
        int steps = t <= OPEN_TICKS ? t : OPEN_TICKS * 2 - t;
        return Math.min(MAX_PEEK, steps * STEP);
    }
}