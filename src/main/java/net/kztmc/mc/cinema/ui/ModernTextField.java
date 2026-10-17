package net.kztmc.mc.cinema.ui;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 26.3対応の1行入力欄。
 *
 * ・Themeによる独自デザイン
 * ・カーソル点滅、横スクロール
 * ・Ctrl+Aによる全選択
 * ・Ctrl+Vによる貼り付け
 * ・Shift+矢印キーによる範囲選択
 * ・マウスクリックによるカーソル移動
 * ・選択範囲の削除、入力による置換
 * ・SDL向けテキスト入力フォーカス通知
 */
public class ModernTextField extends AbstractWidget {

    private static final float TEXT_SCALE = 0.9f;

    private String value = "";
    private String hint = "";
    private int maxLength = 64;

    /** カーソル位置 */
    private int cursor;

    /** 選択範囲の固定端。cursorと異なる場合に選択中 */
    private int selectionAnchor;

    /** 横スクロール量 */
    private double scrollPx;

    private Consumer<String> onChange = s -> {};
    private Consumer<String> onEnter = s -> {};
    private IntPredicate charFilter = cp -> true;

    public ModernTextField(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
    }

    // --------------------------------------------------
    // 設定
    // --------------------------------------------------

    public ModernTextField setHint(String hint) {
        this.hint = hint == null ? "" : hint;
        return this;
    }

    public ModernTextField setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);

        if (value.length() > this.maxLength) {
            value = truncate(value, this.maxLength);
            cursor = Math.min(cursor, value.length());
            selectionAnchor = Math.min(selectionAnchor, value.length());
            onChange.accept(value);
        }

        return this;
    }

    /** 初期値の設定。onChangeは呼ばない */
    public ModernTextField setValue(String value) {
        String text = value == null ? "" : value;

        this.value = truncate(text, maxLength);
        this.cursor = this.value.length();
        this.selectionAnchor = this.cursor;
        this.scrollPx = 0;

        return this;
    }

    public String getValue() {
        return value;
    }

    public ModernTextField setOnChange(Consumer<String> onChange) {
        this.onChange = onChange == null ? s -> {} : onChange;
        return this;
    }

    public ModernTextField setOnEnter(Consumer<String> onEnter) {
        this.onEnter = onEnter == null ? s -> {} : onEnter;
        return this;
    }

    public ModernTextField setCharFilter(IntPredicate charFilter) {
        this.charFilter = charFilter == null ? cp -> true : charFilter;
        return this;
    }

    // --------------------------------------------------
    // 文字列操作
    // --------------------------------------------------

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        int end = maxLength;

        // UTF-16サロゲートペアを途中で切らない
        if (end > 0
                && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }

        return text.substring(0, end);
    }

    private int clampPosition(int position) {
        return Math.max(0, Math.min(position, value.length()));
    }

    private void setCursor(int position, boolean extendSelection) {
        cursor = clampPosition(position);

        if (!extendSelection) {
            selectionAnchor = cursor;
        }
    }

    private int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    private int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    private boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }

        int start = selectionStart();
        int end = selectionEnd();

        value = value.substring(0, start) + value.substring(end);

        cursor = start;
        selectionAnchor = start;
    }

    private void notifyChanged() {
        onChange.accept(value);
    }

    // --------------------------------------------------
    // 寸法
    // --------------------------------------------------

    private float scale() {
        return Theme.fontScale() * TEXT_SCALE;
    }

    private int widthOf(String text) {
        return Math.round(
                Minecraft.getInstance().font.width(text) * scale()
        );
    }

    private int textH() {
        return Math.max(3, Math.round(8 * scale()));
    }

    private int textIndexAt(int position, int direction) {
        position = clampPosition(position);

        if (direction < 0 && position > 0) {
            return value.offsetByCodePoints(position, -1);
        }

        if (direction > 0 && position < value.length()) {
            return value.offsetByCodePoints(position, 1);
        }

        return position;
    }

    // --------------------------------------------------
    // 描画
    // --------------------------------------------------

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor g,
            int mx,
            int my,
            float delta
    ) {
        Font font = Minecraft.getInstance().font;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean focused = isFocused();

        Theme.roundRect(g, x, y, w, h, Theme.SURFACE);

        if (focused) {
            g.outline(x, y, w, h, Theme.ACCENT);
        }

        int pad = Theme.u(5);
        int innerL = x + pad;
        int innerW = Math.max(1, w - pad * 2);

        cursor = clampPosition(cursor);
        selectionAnchor = clampPosition(selectionAnchor);

        // カーソルが見える位置にスクロールする
        int cursorPx = widthOf(value.substring(0, cursor));

        if (cursorPx - scrollPx > innerW - 1) {
            scrollPx = cursorPx - innerW + 1;
        }

        if (cursorPx - scrollPx < 0) {
            scrollPx = cursorPx;
        }

        scrollPx = Math.max(0, scrollPx);

        int ty = y + (h - textH()) / 2;
        int textX = innerL - (int) scrollPx;

        g.enableScissor(
                innerL,
                y + 1,
                innerL + innerW,
                y + h - 1
        );

        if (value.isEmpty()) {
            Theme.text(
                    g,
                    font,
                    hint,
                    innerL,
                    ty,
                    Theme.TEXT_DIM,
                    scale()
            );
        } else {
            // 選択範囲を描画
            if (focused && hasSelection()) {
                int startX = textX
                        + widthOf(value.substring(0, selectionStart()));

                int endX = textX
                        + widthOf(value.substring(0, selectionEnd()));

                g.fill(
                        startX,
                        ty - 1,
                        Math.max(startX + 1, endX),
                        ty + textH() + 1,
                        Theme.ACCENT
                );
            }

            Theme.text(
                    g,
                    font,
                    value,
                    textX,
                    ty,
                    Theme.TEXT,
                    scale()
            );
        }

        // カーソル
        if (focused && !hasSelection()
                && (System.currentTimeMillis() / 500) % 2 == 0) {

            int cx = innerL + cursorPx - (int) scrollPx;

            g.fill(
                    cx,
                    ty - 1,
                    cx + Math.max(1, Theme.u(1)),
                    ty + textH() + 1,
                    Theme.TEXT
            );
        }

        g.disableScissor();
    }

    // --------------------------------------------------
    // 文字入力
    // --------------------------------------------------

    private void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        StringBuilder accepted = new StringBuilder();

        text.codePoints()
                .filter(charFilter)
                .forEach(accepted::appendCodePoint);

        if (accepted.isEmpty() && !hasSelection()) {
            return;
        }

        int start = selectionStart();
        int end = selectionEnd();

        int available = maxLength - (value.length() - (end - start));

        if (available < 0) {
            return;
        }

        String insertion = truncate(accepted.toString(), available);

        // 選択範囲を削除して、その位置に挿入
        value = value.substring(0, start)
                + insertion
                + value.substring(end);

        cursor = start + insertion.length();
        selectionAnchor = cursor;

        notifyChanged();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!isActive() || !isFocused()) {
            return false;
        }

        if (!event.isAllowedChatCharacter()) {
            return false;
        }

        insert(event.codepointAsString());

        return true;
    }

    // --------------------------------------------------
    // キー入力
    // --------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isActive() || !isFocused()) {
            return false;
        }

        /*
         * Minecraft 26.3のEditBoxと同じキーイベントAPIを利用。
         * 以下の数値はSDLのキーコード。
         */
        int key = event.shortcutKey();
        boolean shift = event.hasShiftDown();

        // Backspace
        if (key == 8) {
            if (hasSelection()) {
                deleteSelection();
                notifyChanged();
            } else if (cursor > 0) {
                int previous = textIndexAt(cursor, -1);

                value = value.substring(0, previous)
                        + value.substring(cursor);

                cursor = previous;
                selectionAnchor = cursor;

                notifyChanged();
            }

            return true;
        }

        // Delete
        if (key == 127) {
            if (hasSelection()) {
                deleteSelection();
                notifyChanged();
            } else if (cursor < value.length()) {
                int next = textIndexAt(cursor, 1);

                value = value.substring(0, cursor)
                        + value.substring(next);

                selectionAnchor = cursor;

                notifyChanged();
            }

            return true;
        }

        // Home
        if (key == 1073741898) {
            setCursor(0, shift);
            return true;
        }

        // End
        if (key == 1073741901) {
            setCursor(value.length(), shift);
            return true;
        }

        // Right
        if (key == 1073741903) {
            if (!shift && hasSelection()) {
                setCursor(selectionEnd(), false);
            } else {
                setCursor(textIndexAt(cursor, 1), shift);
            }

            return true;
        }

        // Left
        if (key == 1073741904) {
            if (!shift && hasSelection()) {
                setCursor(selectionStart(), false);
            } else {
                setCursor(textIndexAt(cursor, -1), shift);
            }

            return true;
        }

        // Ctrl+A：全選択
        if (event.isSelectAll()) {
            selectionAnchor = 0;
            cursor = value.length();
            return true;
        }

        // Ctrl+C：選択範囲をコピー
        if (event.isCopy()) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        value.substring(selectionStart(), selectionEnd())
                );
            }

            return true;
        }

        // Ctrl+V：選択範囲を貼り付け文字列で置換
        if (event.isPaste()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }

        // Ctrl+X：選択範囲を切り取り
        if (event.isCut()) {
            if (hasSelection()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        value.substring(selectionStart(), selectionEnd())
                );

                deleteSelection();
                notifyChanged();
            }

            return true;
        }

        // Enter
        // SDLのReturnキーコードは13、キーパッドEnterは1073741912
        if (key == 13 || key == 1073741912) {
            onEnter.accept(value);
            return true;
        }

        return false;
    }

    // --------------------------------------------------
    // マウス入力
    // --------------------------------------------------

    @Override
    public boolean mouseClicked(
            MouseButtonEvent event,
            boolean doubleClick
    ) {
        if (!isActive()
                || !visible
                || !Theme.isClick(event.button())
                || !isMouseOver(event.x(), event.y())) {
            return false;
        }

        double rel = event.x()
                - (getX() + Theme.u(5))
                + scrollPx;

        int best = value.length();

        for (int i = 0; i <= value.length();) {
            int currentWidth = widthOf(value.substring(0, i));

            if (currentWidth >= rel) {
                if (i > 0) {
                    int previous = textIndexAt(i, -1);
                    int previousWidth = widthOf(value.substring(0, previous));

                    best = rel - previousWidth < currentWidth - rel
                            ? previous
                            : i;
                } else {
                    best = 0;
                }

                break;
            }

            if (i == value.length()) {
                best = value.length();
                break;
            }

            i = textIndexAt(i, 1);
        }

        // 通常クリックでは選択を解除
        setCursor(best, false);

        return true;
    }

    // --------------------------------------------------
    // SDLテキスト入力フォーカス
    // --------------------------------------------------

    @Override
    public void setFocused(boolean focused) {
        if (isFocused() == focused) {
            return;
        }

        super.setFocused(focused);

        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}