package net.kztmc.mc.cinema.ui.panel.settings;

import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSettings;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.widget.ColorPicker;
import net.kztmc.mc.cinema.ui.widget.ColorSwatch;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 設定画面(独立した画面)。いまは、テーマカラー。
 * 色の四角を押すと、パレットが、小画面として手前に重なって開く。変更は、その場で反映して、閉じるときに保存する。
 */
public class SettingsScreen extends Screen {
    private static final int[] PRESETS = {0x5B9DFF, 0x3CC8E6, 0x4CC38A, 0xA3D95B, 0xF2C94C,
            0xFF8A4C, 0xF0596B, 0xF277B8, 0x9B7BFF, 0x8D95A8};

    private final Screen parent;
    private ColorPicker picker;       // 部品としては登録せず、小画面の中で描く
    private ModernTextField hexBox;
    private boolean popupOpen;

    private int panelX, panelY, panelW, panelH, pad;
    private int titleY, headingY, hexLabelX, hexLabelY;
    private int popX, popY, popW, popH;

    public SettingsScreen(Screen parent) {
        super(Component.literal("設定"));
        this.parent = parent;
    }

    @Override
    public void added() {
        UiSounds.open();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** どの閉じ方でも、画面が外れるときに保存する */
    @Override
    public void removed() {
        UiSettings.save();
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    @Override
    protected void init() {
        pad = Math.max(3, Theme.u(8));
        int gap = Math.max(1, Theme.u(4));
        int th = Theme.textH();
        int btnH = Math.max(Theme.u(22), 22);
        int boxH = Math.max(th + 3, Theme.u(14));

        panelW = Math.min(width - Theme.u(20), Theme.u(260));
        panelH = pad + (th + Theme.u(5)) + (th + Theme.u(4)) + boxH + gap * 2 + btnH + pad;
        panelX = (width - panelW) / 2;
        panelY = Math.max(Theme.u(5), (height - panelH) / 2);

        int x = panelX + pad;
        int w = panelW - pad * 2;
        int y = panelY + pad;

        titleY = y;
        y += th + Theme.u(5);
        headingY = y;
        y += th + Theme.u(4);

        // 色の四角(押すと、パレットが開く)と、HEX
        int swatchY = y;
        addRenderableWidget(new ColorSwatch(x, swatchY, boxH, boxH, Theme::themeColor, () -> popupOpen = !popupOpen));

        hexLabelX = x + boxH + gap;
        hexLabelY = swatchY + (boxH - th) / 2;
        int fx = hexLabelX + Theme.u(30);
        hexBox = new ModernTextField(fx, swatchY, x + w - fx, boxH);
        hexBox.setHint("RRGGBB");
        hexBox.setMaxLength(7);
        hexBox.setCharFilter(cp -> cp < 128 && (Character.digit(cp, 16) >= 0 || cp == '#'));
        hexBox.setValue(hex(Theme.themeColor()));
        hexBox.setOnChange(this::onHex);
        addRenderableWidget(hexBox);
        y += boxH + gap * 2;

        // ボタン
        int half = (w - gap) / 2;
        addRenderableWidget(new ModernButton(x, y, half, btnH, "初期値に戻す", ModernButton.Style.DEFAULT, () -> {
            Theme.setThemeColor(Theme.DEFAULT_THEME);
            rebuildWidgets();
        }));
        addRenderableWidget(new ModernButton(x + half + gap, y, w - half - gap, btnH, "閉じる",
                ModernButton.Style.PRIMARY, this::onClose));

        // パレットの小画面: 色の四角の下。入らなければ、四角の上
        int popPad = Math.max(3, Theme.u(6));
        int pickerW = Math.min(w, Theme.u(170));
        int pickerH = Theme.u(80) + gap + Theme.u(14);
        popW = pickerW + popPad * 2;
        popH = pickerH + popPad * 2;
        popX = Math.max(2, Math.min(x, width - popW - 2));
        popY = swatchY + boxH + gap;
        if (popY + popH > height - 2) popY = Math.max(2, swatchY - gap - popH);

        picker = new ColorPicker(popX + popPad, popY + popPad, pickerW, pickerH, Theme.themeColor(), PRESETS, rgb -> {
            Theme.setThemeColor(rgb);
            hexBox.setValue(hex(rgb));
        });
    }

    /** HEXの入力: 6桁そろったら、反映する */
    private void onHex(String text) {
        String s = text.startsWith("#") ? text.substring(1) : text;
        if (s.length() != 6) return;
        try {
            int rgb = Integer.parseInt(s, 16);
            Theme.setThemeColor(rgb);
            picker.setRgb(rgb);
        } catch (NumberFormatException ignored) {
            // 数字として読めなければ、そのまま
        }
    }

    private boolean inPopup(double mx, double my) {
        return mx >= popX && mx < popX + popW && my >= popY && my < popY + popH;
    }

    // ---- 入力: パレットが開いている間は、小画面が先に受ける ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popupOpen) {
            if (inPopup(event.x(), event.y())) {
                picker.mouseClicked(event, doubleClick);
                return true;   // 小画面の中は、ほかへ渡さない
            }
            popupOpen = false;   // 外を押したら、閉じる
            // 色の四角を押したときは、閉じるだけにする(開き直さない)
            if (event.x() >= panelX && event.x() < panelX + panelW && event.y() < popY + popH
                    && isOverSwatch(event.x(), event.y())) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean isOverSwatch(double mx, double my) {
        for (var child : children()) {
            if (child instanceof ColorSwatch s && s.isMouseOver(mx, my)) return true;
        }
        return false;
    }

    /** ドラッグは、パレットへ直接届ける(ボタン番号に関係なく動く) */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (picker != null && picker.dragging()) {
            picker.drag(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (picker != null && picker.dragging()) {
            picker.release();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Esc: パレットが開いていれば、パレットだけ閉じる(画面は閉じない) */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popupOpen && event.shortcutKey() == 27) {
            popupOpen = false;
            return true;
        }
        return super.keyPressed(event);
    }

    // ---- 描画 ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        Theme.panel(g, panelX, panelY, panelW, panelH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // 小画面が開いている間は、下の部品に、ホバーの表示を出さない
        int mx = popupOpen ? -1 : mouseX;
        int my = popupOpen ? -1 : mouseY;
        super.extractRenderState(g, mx, my, delta);

        Theme.text(g, font, "設定", panelX + pad, titleY, Theme.ACCENT);
        Theme.text(g, font, "テーマカラー", panelX + pad, headingY, Theme.SECTION);
        Theme.text(g, font, "HEX", hexLabelX, hexLabelY, Theme.TEXT_DIM);

        if (popupOpen) {
            g.nextStratum();   // ほかの部品より、手前に描く
            Theme.roundRect(g, popX - 1, popY - 1, popW + 2, popH + 2, Theme.ACCENT);
            Theme.roundRect(g, popX, popY, popW, popH, Theme.POPUP);
            picker.extractRenderState(g, mouseX, mouseY, delta);
        }
    }
}