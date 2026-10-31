package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 確認ダイアログ。画面の中央に、すべての上へ重ねて出す。表示中は、後ろの画面は操作できない。
 *   Enter(またはテンキーのEnter): 実行  /  Esc: キャンセル
 * 描画と入力は、画面(CinemaScreen)が呼び出す。
 */
public final class ConfirmDialog {
    /** 後ろの画面を暗くする色 */
    private static final int DIM = 0x66000000;
    /** パネルの背景(後ろが透けないよう、ほぼ不透明) */
    private static final int BACKGROUND = 0xF20F1218;

    private boolean open;
    private Font font;
    private int x, y, w, h, pad, lineH;
    private List<String> lines = new ArrayList<>();
    private ModernButton confirmButton;
    private ModernButton cancelButton;
    private Runnable onConfirm = () -> {};

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public void open(Font font, Layout lay, int screenW, int screenH, String message, String confirmLabel,
                     Runnable onConfirm) {
        this.font = font;
        this.onConfirm = onConfirm;
        this.pad = lay.pad() + Theme.u(4);
        this.lineH = lay.th() + Theme.u(3);

        w = Theme.u(230);
        lines = wrap(font, message, w - pad * 2);
        h = pad * 2 + lines.size() * lineH + Theme.u(8) + lay.btnH();
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;

        int bw = (w - pad * 2 - lay.gap()) / 2;
        int by = y + h - pad - lay.btnH();
        confirmButton = new ModernButton(x + pad, by, bw, lay.btnH(), confirmLabel, ModernButton.Style.DANGER, this::confirm);
        cancelButton = new ModernButton(x + pad + bw + lay.gap(), by, bw, lay.btnH(), "キャンセル",
                ModernButton.Style.DEFAULT, this::close);
        open = true;
    }

    private void confirm() {
        close();
        onConfirm.run();
    }

    public void extract(GuiGraphicsExtractor g, int screenW, int screenH, int mouseX, int mouseY, float delta) {
        if (!open) return;
        g.fill(0, 0, screenW, screenH, DIM);
        Theme.roundRect(g, x, y, w, h, BACKGROUND);
        g.outline(x, y, w, h, Theme.ACCENT);

        int ty = y + pad;
        for (String line : lines) {
            Theme.text(g, font, line, x + pad, ty, Theme.TEXT);
            ty += lineH;
        }
        confirmButton.extractRenderState(g, mouseX, mouseY, delta);
        cancelButton.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** 表示中は、すべてのクリックを受ける(後ろへ渡さない) */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!open) return false;
        if (!confirmButton.mouseClicked(event, false)) cancelButton.mouseClicked(event, false);
        return true;
    }

    /** 表示中は、すべてのキーを受ける。SDLのキーコード(shortcutKey): Enter 13 / テンキーEnter 1073741912 / Esc 27 */
    public boolean keyPressed(KeyEvent event) {
        if (!open) return false;
        int key = event.shortcutKey();
        if (key == 13 || key == 1073741912) confirm();
        else if (key == 27) close();
        return true;
    }

    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            line.append(text.charAt(i));
            if (Theme.textWidth(font, line.toString()) > maxWidth && line.length() > 1) {
                char last = line.charAt(line.length() - 1);
                line.setLength(line.length() - 1);
                out.add(line.toString());
                line = new StringBuilder().append(last);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }
}