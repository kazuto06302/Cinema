package net.kztmc.mc.cinema.ui;

import java.util.List;
import java.util.UUID;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * 撮影用の編集画面の骨格。各パネルの中身は、別のクラスにある。
 *   LeftPanel  : 左(モブ追加・NPC一覧・プレイヤー追加)
 *   RightPanel : 右(選択中のNPCの編集)
 *   TunerPanel : 下(devモードのアイコン調整)
 * ここは、画面を開く処理、マウス・キーの振り分け、画面の作り直し、確認ダイアログ・右クリックメニューを受け持つ。
 * ゲーム画面は暗くしない。
 */
public class CinemaScreen extends Screen implements PanelHost {
    private static boolean openRequested;

    private final LeftPanel left = new LeftPanel();
    private final RightPanel right = new RightPanel();
    private final TunerPanel tuner = new TunerPanel();
    private final ContextMenu menu = new ContextMenu();
    private final ConfirmDialog confirm = new ConfirmDialog();

    private Layout layout;
    private boolean hasRight;
    private boolean hasTuner;
    private boolean pendingRefresh;
    private ModernDropdown openDropdown;
    private Draggable pressed;   // 押されているウィジェット(ドラッグの届け先)

    public CinemaScreen() {
        super(Component.literal("Cinema"));
    }

    // ================= 開く処理(チャットが閉じてから開く) =================

    public static void requestOpen() {
        openRequested = true;
    }

    public static void tickOpen(Minecraft mc) {
        if (openRequested && mc.gui.screen() == null && mc.level != null) {
            openRequested = false;
            // 26.2 で Minecraft#setScreen は setScreenAndShow に改名されている
            mc.setScreenAndShow(new CinemaScreen());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** ゲーム画面を暗くしない(撮影構図を見ながら編集するため) */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    // ================= PanelHost =================

    @Override
    public <T extends AbstractWidget> T add(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public void refresh() {
        pendingRefresh = true;
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public void onDropdownOpen(ModernDropdown dropdown) {
        if (openDropdown != null && openDropdown != dropdown) openDropdown.close();
        openDropdown = dropdown;
    }

    @Override
    public void focus(GuiEventListener widget) {
        setInitialFocus(widget);
    }

    @Override
    public Screen screen() {
        return this;
    }

    @Override
    public void confirmDelete(UUID id) {
        String name = NpcManager.get(id).map(NpcNames::of).orElse("NPC");
        menu.close();
        confirm.open(this.font, layout, this.width, this.height,
                "「" + name + "」を削除しますか?(元に戻せません)", "削除", () -> {
                    NpcManager.remove(id);
                    refresh();
                });
    }

    @Override
    public void openContextMenu(int x, int y, List<ContextMenu.Entry> entries) {
        menu.open(this.font, layout, this.width, this.height, x, y, entries);
    }

    // ================= 作り直し =================

    @Override
    protected void init() {
        layout = Layout.of(this.width, this.height);
        hasRight = false;
        hasTuner = false;
        openDropdown = null;
        pressed = null;

        left.build(layout, this);
        NpcManager.selectedData().ifPresent(d -> {
            hasRight = true;
            right.build(layout, this, d);
        });

        if (CinemaState.devMode && left.isMobsTab()) {
            hasTuner = true;
            tuner.build(layout, this);
        }
    }

    // ================= マウス・キー =================

    /** いま、文字を入力している数値欄(なければ null) */
    private ModernNumberField editingField() {
        for (GuiEventListener child : children()) {
            if (child instanceof ModernNumberField field && field.isEditing()) return field;
        }
        return null;
    }

    /** 検索欄・ユーザー名欄に、フォーカスがあるか(あれば、Del/Backspace は文字の削除に使う) */
    private boolean textFieldFocused() {
        return getFocused() instanceof ModernTextField field && field.isFocused();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 確認ダイアログの表示中は、ほかを操作できない
        if (confirm.isOpen()) return confirm.mouseClicked(event);

        // 右クリックのメニュー(中のクリックは、ここで受ける。外のクリックは、閉じて、そのまま下へ通す)
        if (menu.isOpen() && menu.mouseClicked(event)) return true;

        // NPCリストの上の右クリック: メニューを開く
        if (Theme.isRightClick(event.button()) && left.rightClick(event)) return true;

        // 入力中の数値欄の外をクリックしたら、確定する
        ModernNumberField editing = editingField();
        if (editing != null && !editing.isMouseOver(event.x(), event.y())) {
            editing.commitEditing();
        }

        // 開いているドロップダウンの選択肢が、他のウィジェットより先にクリックを受ける
        if (openDropdown != null && openDropdown.isOpen()) {
            if (openDropdown.popupClicked(event)) {
                openDropdown = null;
                return true;
            }
            if (!openDropdown.isMouseOver(event.x(), event.y())) {
                openDropdown.close();   // 外をクリックしたら閉じる(ボタン自身のクリックは、ボタンが開閉する)
                openDropdown = null;
            }
        }

        // 通常。ドラッグできるウィジェット(数値欄・スクロールバー)を押したら、覚えておく
        boolean handled = super.mouseClicked(event, doubleClick);
        pressed = null;
        if (handled && Theme.isClick(event.button())) {
            getChildAt(event.x(), event.y()).ifPresent(child -> {
                if (child instanceof Draggable draggable) pressed = draggable;
            });
        }

        // 右パネルの、項目のない所を押した: そこからのドラッグで、パネルをスクロールする
        if (!handled && Theme.isClick(event.button()) && hasRight && right.beginDragScroll(event.x(), event.y())) {
            return true;
        }
        return handled;
    }

    /** この環境では、ドラッグの標準処理にボタン番号が合わないことがあるので、押したウィジェットへ直接届ける */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (confirm.isOpen()) return true;
        if (hasRight && right.isDragScrolling()) {
            right.dragScrollBy(dy);
            return true;
        }
        if (pressed != null) {
            pressed.dragBy(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (confirm.isOpen()) return true;
        if (hasRight && right.isDragScrolling()) {
            right.endDragScroll();
            return true;
        }
        if (pressed != null) {
            Draggable p = pressed;
            pressed = null;
            p.dragEnd(event.x(), event.y());
            return true;
        }
        return super.mouseReleased(event);
    }

    /** ホイール: パネルをスクロールしはじめたら、短い間は、項目(スライダー・数値欄)より先にパネルが受ける */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (confirm.isOpen()) return true;
        if (hasRight && right.wheelLocked(mouseX, mouseY)) {
            return right.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return hasRight && right.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (confirm.isOpen()) return confirm.keyPressed(event);

        int key = event.shortcutKey();
        if (menu.isOpen() && key == 27) {   // Esc: メニューを閉じる
            menu.close();
            return true;
        }

        // 数値を入力している間は、キーを入力欄へ直接渡す(Escで、画面が閉じないように)
        ModernNumberField field = editingField();
        if (field != null) {
            field.keyPressed(event);
            return true;
        }

        // Del / Backspace: 選択中のNPCを削除する(確認ダイアログを出す)。文字入力中は、文字の削除に使う
        if ((key == 8 || key == 127) && !textFieldFocused()) {
            UUID selected = NpcManager.selectedId();
            if (selected != null) {
                confirmDelete(selected);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (confirm.isOpen()) return true;
        ModernNumberField field = editingField();
        if (field != null) return field.charTyped(event);
        return super.charTyped(event);
    }

    // ================= 描画 =================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (pendingRefresh) {
            pendingRefresh = false;
            left.saveScroll();
            rebuildWidgets();
        }

        // 確認ダイアログの表示中は、後ろのウィジェットに、マウスが乗っていない扱いにする(ホバーの表示を止める)
        boolean modal = confirm.isOpen();
        int mx = modal ? -1 : mouseX;
        int my = modal ? -1 : mouseY;

        // 選択中のNPCの輪郭(パネルより下に描く)
        UUID selected = NpcManager.selectedId();
        Entity selectedEntity = selected == null ? null : NpcManager.entityOf(selected);
        if (selectedEntity != null) {
            SelectionOutline.draw(g, selectedEntity);
        }

        left.extractBackground(g);
        if (hasRight) right.extractBackground(g);
        if (hasTuner) tuner.extractBackground(g);
        super.extractRenderState(g, mx, my, delta);

        // 上に重ねるもの(下から順に): ドロップダウン → 右クリックメニュー → 確認ダイアログ
        if (openDropdown != null && openDropdown.isOpen()) {
            openDropdown.extractPopup(g, mx, my);
        }
        menu.extract(g, this.font, mx, my);
        confirm.extract(g, this.width, this.height, mouseX, mouseY, delta);
    }
}