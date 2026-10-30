package net.kztmc.mc.cinema.ui;

import java.util.Locale;
import java.util.function.DoubleConsumer;
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
 * 数値の入力欄(位置のX/Y/Z用)。ModernTextField と同じ作りで、ショートカットも揃えてある。
 *  - クリック: 文字入力(Enter 確定 / Esc 取り消し / ほかをクリックで確定)
 *  - 左右にドラッグ: 値が動く(1ドット = 0.05。Shift で×10、Ctrl で×100)
 *  - マウスホイール: 値が動く(0.1。同じく×10、×100)
 *  - 入力中のショートカット: Ctrl+A/C/V/X、Shift+矢印で範囲選択、↑↓で値を増減
 * SDLでは、テキスト入力を有効にしないと文字(charTyped)が届かないので、フォーカスの変化で切り替える。
 * キーと文字は、画面(CinemaScreen)が、編集中の欄へ直接渡す。
 */
public class ModernNumberField extends AbstractWidget implements Draggable {
    private static final double DRAG_PER_PIXEL = 0.05;
    private static final double STEP = 0.1;
    private static final int MAX_LENGTH = 16;
    /** この距離(ドット)以内なら、ドラッグではなくクリックとして扱う */
    private static final double CLICK_SLOP = 3.0;

    /** 入力できる文字 */
    private static final String ALLOWED = "0123456789.,-";

    // SDL のキーコード(ModernTextField と同じ。KeyEvent#shortcutKey() で取る)
    private static final int K_BACKSPACE = 8;
    private static final int K_RETURN = 13;
    private static final int K_ESCAPE = 27;
    private static final int K_DELETE = 127;
    private static final int K_KP_ENTER = 1073741912;
    private static final int K_RIGHT = 1073741903;
    private static final int K_LEFT = 1073741904;
    private static final int K_DOWN = 1073741905;
    private static final int K_UP = 1073741906;
    private static final int K_HOME = 1073741898;
    private static final int K_END = 1073741901;
    // 文字イベントが届かない場合の予備(キーから数字を拾う)
    private static final int K_KP_1 = 1073741913;   // テンキー 1〜9
    private static final int K_KP_0 = 1073741922;
    private static final int K_KP_MINUS = 1073741910;
    private static final int K_KP_PERIOD = 1073741923;

    private final String label;
    private final int labelColor;
    private final DoubleConsumer onChange;
    private double value;

    private boolean editing;
    private String text = "";
    private int cursor;
    private int anchor;   // 選択範囲の固定端。cursor と違えば、選択中

    private boolean charEvents;       // 文字入力のイベント(charTyped)が届く環境か
    private int pendingKeyChar = -1;  // キーのイベントで入れた文字。直後の charTyped が同じなら、二重にならないよう無視する

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

    // ================= 選択範囲 =================

    private boolean hasSelection() {
        return cursor != anchor;
    }

    private int selStart() {
        return Math.min(cursor, anchor);
    }

    private int selEnd() {
        return Math.max(cursor, anchor);
    }

    private void setCursor(int position, boolean extend) {
        cursor = Math.max(0, Math.min(position, text.length()));
        if (!extend) anchor = cursor;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = selStart();
        text = text.substring(0, start) + text.substring(selEnd());
        cursor = start;
        anchor = start;
    }

    /** 選択範囲を、入力した文字で置き換える(選択がなければ、カーソル位置に挿入) */
    private void insert(String raw) {
        StringBuilder accepted = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (ALLOWED.indexOf(c) >= 0) accepted.append(c == ',' ? '.' : c);
        }
        if (accepted.isEmpty()) return;

        int start = selStart();
        int end = selEnd();
        int room = MAX_LENGTH - (text.length() - (end - start));
        if (room <= 0) return;
        String add = accepted.length() > room ? accepted.substring(0, room) : accepted.toString();

        text = text.substring(0, start) + add + text.substring(end);
        cursor = start + add.length();
        anchor = cursor;
    }

    // ================= 描画 =================

    private int tx(Font font, String shown) {
        return getX() + getWidth() - Theme.u(7) - Theme.textWidth(font, shown);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Font font = Minecraft.getInstance().font;
        boolean hov = active && mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
        hover += ((hov || editing ? 1f : 0f) - hover) * 0.35f;

        Theme.roundRect(g, getX(), getY(), getWidth(), getHeight(), Theme.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover));
        if (editing) {
            g.outline(getX(), getY(), getWidth(), getHeight(), Theme.ACCENT);
        }

        int ty = getY() + (getHeight() - Theme.textH()) / 2;
        Theme.text(g, font, label, getX() + Theme.u(7), ty, labelColor);

        String shown = editing ? text : format(value);
        int textX = tx(font, shown);

        // 選択範囲
        if (editing && hasSelection()) {
            int sx = textX + Theme.textWidth(font, text.substring(0, selStart()));
            int ex = textX + Theme.textWidth(font, text.substring(0, selEnd()));
            g.fill(sx, ty - 1, Math.max(sx + 1, ex), ty + Theme.textH() + 1, Theme.ACCENT);
        }
        Theme.text(g, font, shown, textX, ty, Theme.TEXT);

        // カーソル(点滅)
        if (editing && !hasSelection() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = textX + Theme.textWidth(font, text.substring(0, Math.min(cursor, text.length())));
            g.fill(cx, ty - 1, cx + Math.max(1, Theme.u(1)), ty + Theme.textH() + 1, Theme.TEXT);
        }
    }

    // ================= マウス =================

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
        if (pressing && moved <= CLICK_SLOP) {
            if (editing) {
                setCursor(indexAt(mouseX), false);   // 入力中のクリック: カーソルを移動
            } else {
                startEditing();
            }
        }
        pressing = false;
    }

    /** マウスのx座標に、いちばん近い文字の境目 */
    private int indexAt(double mouseX) {
        Font font = Minecraft.getInstance().font;
        int start = tx(font, text);
        int best = text.length();
        for (int i = 0; i <= text.length(); i++) {
            int px = start + Theme.textWidth(font, text.substring(0, i));
            if (px >= mouseX) {
                if (i > 0) {
                    int prev = start + Theme.textWidth(font, text.substring(0, i - 1));
                    best = mouseX - prev < px - mouseX ? i - 1 : i;
                } else {
                    best = 0;
                }
                break;
            }
        }
        return best;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        editing = false;
        change(value + Math.signum(scrollY) * STEP * Modifiers.factor());
        return true;
    }

    // ================= フォーカスと編集の開始・確定 =================

    private void startEditing() {
        // 先にフォーカスを移す(フォーカスの切り替えで setFocused(false) が呼ばれて、確定してしまうので、編集を始める前に行う)
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && !isFocused()) screen.setFocused(this);
        editing = true;
        text = format(value);
        anchor = 0;                // 全体を選択した状態から始める(最初の入力で置き換わる)
        cursor = text.length();
        pendingKeyChar = -1;
    }

    private void commit() {
        if (!editing) return;
        editing = false;
        try {
            change(Double.parseDouble(text.replace(',', '.')));
        } catch (NumberFormatException ignored) {
            // 数字として読めなければ、元の値のまま
        }
    }

    /** SDLでは、テキスト入力を有効にしないと文字(charTyped)が届かない。フォーカスの変化で切り替える */
    @Override
    public void setFocused(boolean focused) {
        if (isFocused() == focused) return;
        if (!focused) commit();   // ほかをクリックしたら、確定する
        super.setFocused(focused);
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }

    // ================= キー入力 =================

    /** 文字イベントが届かないときの予備: キーコードから、数値入力に使う文字を返す(使わないキーは -1) */
    private static int charOfKey(int key) {
        if (key >= '0' && key <= '9') return key;
        if (key >= K_KP_1 && key <= K_KP_1 + 8) return '1' + (key - K_KP_1);
        return switch (key) {
            case K_KP_0 -> '0';
            case '.', ',', K_KP_PERIOD -> '.';
            case '-', K_KP_MINUS -> '-';
            default -> -1;
        };
    }

    private double currentValue() {
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing) return false;

        int key = event.shortcutKey();
        boolean shift = event.hasShiftDown();

        // Ctrl+A / C / V / X
        if (event.isSelectAll()) {
            anchor = 0;
            cursor = text.length();
            return true;
        }
        if (event.isCopy()) {
            if (hasSelection()) Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selStart(), selEnd()));
            return true;
        }
        if (event.isPaste()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (event.isCut()) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selStart(), selEnd()));
                deleteSelection();
            }
            return true;
        }

        switch (key) {
            case K_RETURN, K_KP_ENTER -> commit();
            case K_ESCAPE -> editing = false;
            case K_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    anchor = cursor;
                }
            }
            case K_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    anchor = cursor;
                }
            }
            case K_LEFT -> {
                if (!shift && hasSelection()) setCursor(selStart(), false);
                else setCursor(cursor - 1, shift);
            }
            case K_RIGHT -> {
                if (!shift && hasSelection()) setCursor(selEnd(), false);
                else setCursor(cursor + 1, shift);
            }
            case K_HOME -> setCursor(0, shift);
            case K_END -> setCursor(text.length(), shift);
            case K_UP, K_DOWN -> {
                // ↑↓: 値を増減する(Shift で×10、Ctrl で×100)
                double factor = event.hasControlDown() ? 100.0 : shift ? 10.0 : 1.0;
                double next = currentValue() + (key == K_UP ? 1 : -1) * STEP * factor;
                change(next);
                text = format(value);
                cursor = text.length();
                anchor = cursor;
            }
            default -> {
                // 文字イベントが届く環境では、そちらで入れる。届かない環境だけ、キーから入れる
                int typed = charOfKey(key);
                if (typed >= 0 && !charEvents) {
                    insert(String.valueOf((char) typed));
                    pendingKeyChar = typed;
                }
            }
        }
        return true;   // 編集中は、キーを画面の他の処理へ渡さない
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing) return false;
        charEvents = true;
        if (!event.isAllowedChatCharacter()) return true;

        int c = event.codepoint();
        if (c == pendingKeyChar) {   // 直前に、キーのイベントで入れた文字(二重に入れない)
            pendingKeyChar = -1;
            return true;
        }
        insert(event.codepointAsString());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}