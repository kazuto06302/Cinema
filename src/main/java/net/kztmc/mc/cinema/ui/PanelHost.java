package net.kztmc.mc.cinema.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** 各パネル(左・右・下)が、画面(CinemaScreen)に頼む機能。 */
public interface PanelHost {
    /** ウィジェットを、画面に追加する */
    <T extends AbstractWidget> T add(T widget);

    /** ウィジェットのクリック処理中に作り直すと危ないので、次の描画の頭で、画面を作り直す */
    void refresh();

    Font font();

    /** ドロップダウンが開いた(ほかのものは閉じて、描画とクリックを先に受けられるようにする) */
    void onDropdownOpen(ModernDropdown dropdown);

    /** キー入力を、このウィジェットへ向ける */
    void focus(GuiEventListener widget);

    /** 親の画面(装飾の画面から戻るため) */
    Screen screen();

    /** NPCを削除する(確認のダイアログを、画面の中央に出して、「削除」を押したときだけ削除する) */
    void confirmDelete(java.util.UUID id);

    /** 右クリックのメニューを、その位置に開く */
    void openContextMenu(int x, int y, java.util.List<ContextMenu.Entry> entries);
}