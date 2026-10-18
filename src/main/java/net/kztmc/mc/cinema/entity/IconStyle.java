package net.kztmc.mc.cinema.entity;

/**
 * モブごとの表示スタイル。
 *   fit   : 大きさの倍率(1.0が標準、小さいほど小さく描く)
 *   yaw   : 横回転(度)。0=正面、90=真横。プラスで画面の左向き
 *   pitch : 縦回転(度)。上下の傾き。符号が逆なら MobIcons.PITCH_SIGN を反転
 *   roll  : Z軸回転(度)。画面の中心を軸にアイコン全体を傾ける。符号が逆なら ROLL_SIGN を反転
 *   height: 高さ(%)。アイコン枠の高さに対する割合で上下にずらす。+で上。逆なら HEIGHT_SIGN を反転
 */
public record IconStyle(float fit, float yaw, float pitch, float roll, float height) {
    public static final IconStyle DEFAULT = new IconStyle(1f, 20f, 0f, 0f, 0f);

    /** 3項目だけの指定。roll と height は 0 */
    public static IconStyle of(float fit, float yaw, float pitch) {
        return new IconStyle(fit, yaw, pitch, 0f, 0f);
    }

    public static IconStyle of(float fit, float yaw, float pitch, float roll, float height) {
        return new IconStyle(fit, yaw, pitch, roll, height);
    }

    /** 大きさだけ変えて、向きは標準のまま */
    public static IconStyle fit(float fit) {
        return new IconStyle(fit, DEFAULT.yaw, DEFAULT.pitch, 0f, 0f);
    }

    public IconStyle withFit(float v) {
        return new IconStyle(v, yaw, pitch, roll, height);
    }

    public IconStyle withYaw(float v) {
        return new IconStyle(fit, v, pitch, roll, height);
    }

    public IconStyle withPitch(float v) {
        return new IconStyle(fit, yaw, v, roll, height);
    }

    public IconStyle withRoll(float v) {
        return new IconStyle(fit, yaw, pitch, v, height);
    }

    public IconStyle withHeight(float v) {
        return new IconStyle(fit, yaw, pitch, roll, v);
    }
}