package net.kztmc.mc.cinema.ui;

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

/**
 * 撮影用の編集画面の骨格。各パネルの中身は、別のクラスにある。
 *   LeftPanel  : 左(モブ追加・NPC一覧・プレイヤー追加)
 *   RightPanel : 右(選択中のNPCの編集)
 *   TunerPanel : 下(devモードのアイコン調整)
 * ここは、画面を開く処理、マウス・キーの振り分け、画面の作り直しだけを受け持つ。
 * ゲーム画面は暗くしない。上部中央のボタンでUI全体を隠せる。
 */
public class CinemaScreen extends Screen implements PanelHost {
    private static boolean openRequested;
    private static boolean collapsed;

    private final LeftPanel left = new LeftPanel();
    private final RightPanel right = new RightPanel();
    private final TunerPanel tuner = new TunerPanel();

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

    // ================= 作り直し =================

    @Override
    protected void init() {
        layout = Layout.of(this.width, this.height);
        hasRight = false;
        hasTuner = false;
        openDropdown = null;
        pressed = null;

        int pillW = Theme.u(56);
        int pillX = (this.width - (pillW * 2 + layout.gap())) / 2;
        addRenderableWidget(new ModernButton(pillX, layout.m(), pillW, layout.btnH(),
                collapsed ? "UI表示" : "UI非表示", ModernButton.Style.DEFAULT, () -> {
            collapsed = !collapsed;
            refresh();
        }));
        if (collapsed) {
            NpcManager.setHighlight(null);   // UIを隠したら、選択中の光るアウトラインも消す
            return;
        }

        left.build(layout, this);
        NpcManager.selectedData().ifPresentOrElse(d -> {
            hasRight = true;
            right.build(layout, this, d);
            NpcManager.setHighlight(d.id());   // 選択中のNPCのまわりを、光らせる
        }, () -> NpcManager.setHighlight(null));

        if (CinemaState.devMode && left.isMobsTab()) {
            hasTuner = true;
            tuner.build(layout, this);
        }
    }

    /** 画面を閉じる・別の画面へ移るときに、選択中の光るアウトラインを消す(撮影に写らないように) */
    @Override
    public void removed() {
        NpcManager.setHighlight(null);
        super.removed();
    }

    // ================= マウス・キー =================

    /** いま、文字を入力している数値欄(なければ null) */
    private ModernNumberField editingField() {
        for (GuiEventListener child : children()) {
            if (child instanceof ModernNumberField field && field.isEditing()) return field;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
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
        return handled;
    }

    /** この環境では、ドラッグの標準処理にボタン番号が合わないことがあるので、押したウィジェットへ直接届ける */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed != null) {
            pressed.dragBy(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed != null) {
            Draggable p = pressed;
            pressed = null;
            p.dragEnd(event.x(), event.y());
            return true;
        }
        return super.mouseReleased(event);
    }

    /** ホイール: スライダー・数値欄が使わなかったときは、右パネルをスクロールする */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return hasRight && right.mouseScrolled(mouseX, mouseY, scrollY);
    }

    /** 数値を入力している間は、キーと文字を入力欄へ直接渡す(Escで、画面が閉じないように) */
    @Override
    public boolean keyPressed(KeyEvent event) {
        ModernNumberField field = editingField();
        if (field != null) {
            field.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
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

        if (!collapsed) {
            left.extractBackground(g);
            if (hasRight) right.extractBackground(g);
            if (hasTuner) tuner.extractBackground(g);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);

        if (openDropdown != null && openDropdown.isOpen()) {
            openDropdown.extractPopup(g, mouseX, mouseY);
        }
    }
}