package net.kztmc.mc.cinema.ui.core;

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

    /** 左パネルを広げているか(画面を開き直しても保持する) */
    public static boolean leftOpen = false;

    // 左パネルの種類
    public enum LeftView { NONE, MAIN, PARTS }

    // 開いている左パネル(画面を開き直しても保持する)
    public static LeftView leftView = LeftView.NONE;

    // パーツ一覧を開く前に、メインのパネルが開いていたか(パーツを閉じたとき、元に戻すため)
    public static boolean mainWasOpen;

    // 右パネルからの、左パネルの切り替えの要求(画面が、次のフレームで受け取る)
    public static LeftView leftRequest;
}