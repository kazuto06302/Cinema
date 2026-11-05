package net.kztmc.mc.cinema.ui;

/** 時間・天候のクライアント側上書き。サーバーやゲームルールには触れない */
public final class ClientOverrides {
    private ClientOverrides() {}

    public static boolean timeOn;
    public static int timeTicks;       // 1日の中の時刻 0〜23999(0 = 6:00)
    public static boolean weatherOn;
    public static int weather;         // 0 晴れ / 1 雨 / 2 雷雨

    /** 上書き前の、実際の時刻(Mixin が毎回更新する。表示用) */
    public static long realTime;

    public static float rain() {
        return weather >= 1 ? 1f : 0f;
    }

    public static float thunder() {
        return weather >= 2 ? 1f : 0f;
    }
}