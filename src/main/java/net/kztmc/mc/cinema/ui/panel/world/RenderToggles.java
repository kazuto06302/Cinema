package net.kztmc.mc.cinema.ui.panel.world;

/** 描画のオン/オフ(クライアント側)。最初は全部オン */
public final class RenderToggles {
    private RenderToggles() {}

    public enum Item {
        SCOREBOARD("スコアボード"),
        ACTION_BAR("アクションバー"),
        TITLE("タイトルテキスト"),
        BOSS_BAR("ボスバー"),
        CHAT("チャット"),
        BLOCKS("ブロック"),
        ENTITIES("エンティティ(NPC・プレイヤー以外)"),
        PARTICLES("パーティクル"),
        NAME_TAGS("ネームタグ"),
        PLAYERS("プレイヤー"),
        BEACON_BEAMS("ビーコンのビーム");

        public final String label;
        private boolean on = true;

        Item(String label) {
            this.label = label;
        }

        public boolean on() {
            return on;
        }

        public void set(boolean value) {
            on = value;
        }
    }

    public static boolean isOn(Item item) {
        return item.on;
    }

    public static void resetAll() {
        for (Item item : Item.values()) item.on = true;
    }
}