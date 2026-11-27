package net.kztmc.mc.cinema.ui.panel.settings;

import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSettings;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.widget.ColorPicker;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernSlider;
import net.kztmc.mc.cinema.ui.widget.ModernTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 設定画面(独立した画面)。いまは、テーマカラーと、パネルの不透明度。変更は、その場で反映して、閉じるときに保存する。 */
public class SettingsScreen extends Screen {
    private static final int[] PRESETS = {0x5B9DFF, 0x3CC8E6, 0x4CC38A, 0xA3D95B, 0xF2C94C,
            0xFF8A4C, 0xF0596B, 0xF277B8, 0x9B7BFF, 0x8D95A8};

    private final Screen parent;
    private ColorPicker picker;
    private ModernTextField hexBox;
    private int panelX, panelY, panelW, panelH, pad;
    private int titleY, headingY, hexLabelX, hexLabelY;

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
        int sh = th + Theme.u(11);
        int pickerH = Theme.u(110) + gap + Theme.u(14);

        panelW = Math.min(width - Theme.u(20), Theme.u(300));
        panelH = pad + (th + Theme.u(5)) + (th + Theme.u(4)) + pickerH + gap + boxH + gap + sh + gap * 2 + btnH + pad;
        panelX = (width - panelW) / 2;
        panelY = Math.max(Theme.u(5), (height - panelH) / 2);

        int x = panelX + pad;
        int w = panelW - pad * 2;
        int y = panelY + pad;

        titleY = y;
        y += th + Theme.u(5);
        headingY = y;
        y += th + Theme.u(4);

        // カラーパレット
        picker = new ColorPicker(x, y, w, pickerH, Theme.themeColor(), PRESETS, rgb -> {
            Theme.setThemeColor(rgb);
            hexBox.setValue(hex(rgb));
        });
        addRenderableWidget(picker);
        y += pickerH + gap;

        // HEX
        int labelW = Theme.u(30);
        hexLabelX = x;
        hexLabelY = y + (boxH - th) / 2;
        hexBox = new ModernTextField(x + labelW, y, w - labelW, boxH);
        hexBox.setHint("RRGGBB");
        hexBox.setMaxLength(7);
        hexBox.setCharFilter(cp -> cp < 128 && (Character.digit(cp, 16) >= 0 || cp == '#'));
        hexBox.setValue(hex(Theme.themeColor()));
        hexBox.setOnChange(this::onHex);
        addRenderableWidget(hexBox);
        y += boxH + gap;

        // 不透明度
        addRenderableWidget(new ModernSlider(x, y, w, sh, "パネルの不透明度", "%", 10, 100,
                Math.round(Theme.opacity() * 100), v -> Theme.setOpacity((float) v / 100f))
                .setDefault(Math.round(Theme.DEFAULT_OPACITY * 100)));
        y += sh + gap * 2;

        // ボタン
        int half = (w - gap) / 2;
        addRenderableWidget(new ModernButton(x, y, half, btnH, "初期値に戻す", ModernButton.Style.DEFAULT, () -> {
            Theme.setThemeColor(Theme.DEFAULT_THEME);
            Theme.setOpacity(Theme.DEFAULT_OPACITY);
            rebuildWidgets();
        }));
        addRenderableWidget(new ModernButton(x + half + gap, y, w - half - gap, btnH, "閉じる",
                ModernButton.Style.PRIMARY, this::onClose));
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

    // ---- ドラッグは、パレットへ直接届ける(ボタン番号に関係なく動く) ----

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

    // ---- 描画 ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        Theme.panel(g, panelX, panelY, panelW, panelH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Theme.text(g, font, "設定", panelX + pad, titleY, Theme.ACCENT);
        Theme.text(g, font, "テーマカラー", panelX + pad, headingY, Theme.SECTION);
        Theme.text(g, font, "HEX", hexLabelX, hexLabelY, Theme.TEXT_DIM);
    }
}