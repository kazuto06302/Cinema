package net.kztmc.mc.cinema.ui;

/**
 * マウスを押したまま動かして操作するウィジェット。
 * この環境では、左ボタンの番号が、標準のドラッグの処理(ボタン0のみ)に合わないことがあるので、
 * 画面(CinemaScreen)が、押したウィジェットを覚えて、ドラッグを、ここへ直接届ける。
 */
public interface Draggable {
    void dragBy(double dx, double dy);

    /** マウスを離した。ほとんど動いていなければ、クリックとして扱う */
    void dragEnd(double mouseX, double mouseY);
}