package net.kztmc.mc.cinema.ui;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 数値の入力欄(位置のX/Y/Z用)。
 *  - クリック: 文字入力(Enter 確定 / Esc 取り消し / ほかをクリックで確定)
 *  - 左右にドラッグ: 値が動く(1ドット = 0.05。Shift で×10、Ctrl で×100)
 *  - マウスホイール: 値が動く(0.1。Shift で×10、Ctrl で×100)
 * キーと文字は、画面(CinemaScreen)が、編集中の欄へ直接渡す。
 */
public class ModernNumberField extends AbstractWidget implements Draggable {
    /** true なら、入力の流れをログに出す(入力できないときの原因調べ用) */
    private static final boolean DEBUG_INPUT = false;

    private static final double DRAG_PER_PIXEL = 0.05;
    private static final double WHEEL_STEP = 0.1;
    /** この距離(ドット)以内なら、ドラッグではなくクリックとして扱う */
    private static final double CLICK_SLOP = 3.0;

    // SDL のスキャンコード。この環境では、KeyEvent#key() がスキャンコードで届く(キーコードではない)
    private static final int SC_1 = 30;          // 1〜9 = 30〜38
    private static final int SC_0 = 39;
    private static final int SC_RETURN = 40;
    private static final int SC_ESCAPE = 41;
    private static final int SC_BACKSPACE = 42;
    private static final int SC_MINUS = 45;
    private static final int SC_COMMA = 54;
    private static final int SC_PERIOD = 55;
    private static final int SC_HOME = 74;
    private static final int SC_DELETE = 76;
    private static final int SC_END = 77;
    private static final int SC_RIGHT = 79;
    private static final int SC_LEFT = 80;
    private static final int SC_KP_MINUS = 86;
    private static final int SC_KP_ENTER = 88;
    private static final int SC_KP_1 = 89;       // テンキー 1〜9 = 89〜97
    private static final int SC_KP_0 = 98;
    private static final int SC_KP_PERIOD = 99;

    private final String label;
    private final int labelColor;
    private final DoubleConsumer onChange;
    private double value;

    private boolean editing;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private boolean replaceAll;   // 編集を始めた直後は、全体が選択されている(最初の入力で置き換わる)

    private boolean charEvents;       // 文字入力のイベント(charTyped)が届く環境か
    private int pendingKeyChar = -1;  // キーのイベントで入れた文字。直後の charTyped が同じ文字なら、二重にならないよう無視する

    private boolean pressing;
    private double moved;
    private float hover;

    public ModernNumberField(int x, int y, int w, int h, String label, int labelColor, double value, DoubleConsumer onChange) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.labelColor = labelColor;
        this.value = value;
        this.onChange = onChange;
    }

    public double getValue() {
        return value;
    }

    public boolean isEditing() {
        return editing;
    }

    /** 画面から呼ぶ: ほかをクリックしたときに、確定する */
    public void commitEditing() {
        commit();
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private void change(double v) {
        value = Math.round(v * 10000.0) / 10000.0;   // 浮動小数点の誤差を丸める
        onChange.accept(value);
    }

    // ---- 描画 ----

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = active && mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        hover += ((hov || editing ? 1f : 0f) - hover) * 0.35f;

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(), Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover));
        if (editing) {
            g.outline(getX(), getY(), getWidth(), getHeight(), Theme.ACCENT);
        }

        int pad = Theme.u(7);
        int ty = getY() + (getHeight() - Theme.textH()) / 2;
        Theme.text(g, font, label, getX() + pad, ty, labelColor);

        String shown = editing ? text.toString() : format(value);
        int tx = getX() + getWidth() - pad - Theme.textWidth(font, shown);
        if (editing && replaceAll) {
            g.fill(tx - 1, ty - 1, tx + Theme.textWidth(font, shown) + 1, ty + Theme.textH() + 1, 0x663C7BD9);
        }
        Theme.text(g, font, shown, tx, ty, Theme.TEXT);

        // カーソル(点滅)
        if (editing && !replaceAll && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + Theme.textWidth(font, text.substring(0, cursor));
            g.fill(cx, ty - 1, cx + 1, ty + Theme.textH() + 1, Theme.TEXT);
        }
    }

    // ---- マウス ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !Theme.isClick(event.button()) || !isMouseOver(event.x(), event.y())) return false;
        pressing = true;
        moved = 0;
        return true;
    }

    @Override
    public void dragBy(double dx, double dy) {
        if (!pressing) return;
        moved += Math.abs(dx);
        if (moved <= CLICK_SLOP) return;
        editing = false;   // ドラッグを始めたら、入力は取り消す
        change(value + dx * DRAG_PER_PIXEL * Modifiers.factor());
    }

    @Override
    public void dragEnd(double mouseX, double mouseY) {
        if (pressing && moved <= CLICK_SLOP) startEditing();
        pressing = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        editing = false;
        change(value + Math.signum(scrollY) * WHEEL_STEP * Modifiers.factor());
        return true;
    }

    // ---- 文字入力 ----

    private void startEditing() {
        // フォーカスも、念のため移す(キーと文字は、画面が直接渡すので、これが無くても動く)
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && !isFocused()) screen.setFocused(this);
        editing = true;
        text.setLength(0);
        text.append(format(value));
        cursor = text.length();
        replaceAll = true;
        pendingKeyChar = -1;
        if (DEBUG_INPUT) Cinema.LOGGER.info("[Cinema] number field '{}': editing started", label);
    }

    private void commit() {
        if (!editing) return;
        editing = false;
        try {
            change(Double.parseDouble(text.toString().replace(',', '.')));
        } catch (NumberFormatException ignored) {
            // 数字として読めなければ、元の値のまま
        }
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) commit();   // ほかをクリックしたら、確定する
        super.setFocused(focused);
    }

    /** スキャンコードから、数値入力に使う文字を返す(使わないキーは -1) */
    private static int charOfKey(int key) {
        if (key >= SC_1 && key <= SC_1 + 8) return '1' + (key - SC_1);
        if (key == SC_0) return '0';
        if (key >= SC_KP_1 && key <= SC_KP_1 + 8) return '1' + (key - SC_KP_1);
        if (key == SC_KP_0) return '0';
        return switch (key) {
            case SC_PERIOD, SC_COMMA, SC_KP_PERIOD -> '.';
            case SC_MINUS, SC_KP_MINUS -> '-';
            default -> -1;
        };
    }

    private void insert(int c) {
        if (replaceAll) {
            text.setLength(0);
            cursor = 0;
            replaceAll = false;
        }
        text.insert(cursor++, (char) c);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing) return false;
        int key = event.key();
        if (DEBUG_INPUT) Cinema.LOGGER.info("[Cinema] number field '{}': key {}", label, key);

        // 数字・小数点・マイナス。文字イベントが届く環境では、そちらで入れるので、ここでは入れない
        int typed = charOfKey(key);
        if (typed >= 0) {
            if (!charEvents) {
                insert(typed);
                pendingKeyChar = typed;
            }
            return true;
        }

        switch (key) {
            case SC_RETURN, SC_KP_ENTER -> commit();
            case SC_ESCAPE -> editing = false;
            case SC_BACKSPACE -> {
                if (replaceAll) {
                    text.setLength(0);
                    cursor = 0;
                } else if (cursor > 0) {
                    text.deleteCharAt(--cursor);
                }
                replaceAll = false;
            }
            case SC_DELETE -> {
                if (replaceAll) {
                    text.setLength(0);
                    cursor = 0;
                } else if (cursor < text.length()) {
                    text.deleteCharAt(cursor);
                }
                replaceAll = false;
            }
            case SC_LEFT -> {
                cursor = replaceAll ? 0 : Math.max(0, cursor - 1);
                replaceAll = false;
            }
            case SC_RIGHT -> {
                cursor = replaceAll ? text.length() : Math.min(text.length(), cursor + 1);
                replaceAll = false;
            }
            case SC_HOME -> {
                cursor = 0;
                replaceAll = false;
            }
            case SC_END -> {
                cursor = text.length();
                replaceAll = false;
            }
            default -> { }
        }
        return true;   // 編集中は、キーを画面の他の処理へ渡さない
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing) return false;
        charEvents = true;
        int c = event.codepoint();
        if (DEBUG_INPUT) Cinema.LOGGER.info("[Cinema] number field '{}': char {}", label, c);

        if (c == pendingKeyChar) {   // 直前に、キーのイベントで入れた文字(二重に入れない)
            pendingKeyChar = -1;
            return true;
        }
        boolean digit = c >= '0' && c <= '9';
        if (!digit && c != '.' && c != ',' && c != '-') return true;
        insert(c == ',' ? '.' : c);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}