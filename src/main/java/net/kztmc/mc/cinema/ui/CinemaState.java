package net.kztmc.mc.cinema.ui;

/** 画面を開き直しても保持する、画面全体の状態(devモードなど)。 */
public final class CinemaState {
    private CinemaState() {}

    /** devモード。保存されない(ゲームを閉じると通常に戻る) */
    public static boolean devMode;

    /** devモードの切り替え後に、検索欄へ入力を戻す */
    public static boolean focusSearch;

    /** アイコン調整で選んでいるモブ */
    public static String tuneId;
    public static String tuneName;
}