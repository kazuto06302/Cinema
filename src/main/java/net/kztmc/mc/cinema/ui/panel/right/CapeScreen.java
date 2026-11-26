package net.kztmc.mc.cinema.ui.panel.right;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.kztmc.mc.cinema.npc.CapeLibrary;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.panel.left.ModernCardGrid;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** ケープを、画像と名前のカードから選ぶ画面。検索もできる。選ぶと、すぐ閉じて、元の画面へ戻る。 */
public class CapeScreen extends Screen {
    private static final int COLS = 5;
    private static final CapeLibrary.Item NONE = new CapeLibrary.Item("", "なし(スキンのケープ)", "なし none");

    private final Screen parent;
    private final String current;
    private final Consumer<String> onPick;

    private String search = "";
    private ModernCardGrid<CapeLibrary.Item> grid;
    private int panelX, panelY, panelW, panelH, pad, hintY;
    private String status = "";
    private long statusUntil;

    public CapeScreen(Screen parent, String current, Consumer<String> onPick) {
        super(Component.literal("ケープを選ぶ"));
        this.parent = parent;
        this.current = current == null ? "" : current;
        this.onPick = onPick;
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

    private List<CapeLibrary.Item> filtered() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<CapeLibrary.Item> out = new ArrayList<>();
        out.add(NONE);   // 先頭は、常に「なし」
        for (CapeLibrary.Item item : CapeLibrary.items()) {
            if (q.isEmpty() || item.searchKey().contains(q)) out.add(item);
        }
        return out;
    }

    @Override
    protected void init() {
        pad = Math.max(3, Theme.u(8));
        panelW = Math.min(width - Theme.u(20), Theme.u(340));
        panelH = Math.min(height - Theme.u(20), Theme.u(300));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int x = panelX + pad;
        int w = panelW - pad * 2;
        int gap = Math.max(1, Theme.u(4));
        int y = panelY + pad + Theme.textH() + Theme.u(5);
        int boxH = Math.max(Theme.textH() + 3, Theme.u(14));
        int btnH = Math.max(Theme.u(22), 22);

        ModernTextField box = new ModernTextField(x, y, w, boxH);
        box.setHint("ケープを検索...");
        box.setMaxLength(40);
        box.setValue(search);
        addRenderableWidget(box);
        y += boxH + gap;

        int closeY = panelY + panelH - pad - btnH;
        hintY = closeY - gap - Theme.textH();
        int gridH = hintY - gap - y;

        grid = new ModernCardGrid<>(x, y, w, gridH, COLS, "該当するケープがありません",
                CapeLibrary.Item::label, this::paint, this::pick);
        grid.setHighlight(item -> item.id().equals(current));
        grid.setLabelScale(0.85f);
        grid.setItems(filtered());
        addRenderableWidget(grid);

        box.setOnChange(s -> {
            search = s;
            grid.setItems(filtered());
            grid.setScroll(0);
        });

        addRenderableWidget(new ModernButton(x, closeY, w, btnH, "閉じる", ModernButton.Style.DEFAULT, this::onClose));
    }

    /** カードの絵: ケープの背中側の面。画像が無いものは「?」 */
    private void paint(GuiGraphicsExtractor g, CapeLibrary.Item item, int x, int y, int size) {
        if (item.id().isEmpty()) {
            Theme.textCentered(g, font, "なし", x + size / 2, y + (size - Theme.textH()) / 2, Theme.TEXT_DIM);
            return;
        }
        CapeLibrary.Loaded cape = CapeLibrary.get(item.id());
        if (cape == null) {
            Theme.textCentered(g, font, "?", x + size / 2, y + (size - Theme.textH()) / 2, Theme.TEXT_DIM);
            return;
        }
        int w = Math.round(size * 10f / 16f);
        int left = x + (size - w) / 2;
        for (int ty = 0; ty < 16; ty++) {
            int y0 = y + Math.round(ty * size / 16f);
            int y1 = y + Math.round((ty + 1) * size / 16f);
            for (int tx = 0; tx < 10; tx++) {
                int argb = cape.preview()[ty * 10 + tx];
                if ((argb >>> 24) == 0) continue;   // 透明な画素は、描かない
                g.fill(left + Math.round(tx * w / 10f), y0, left + Math.round((tx + 1) * w / 10f), y1, argb | 0xFF000000);
            }
        }
    }

    private void pick(CapeLibrary.Item item) {
        if (!item.id().isEmpty() && CapeLibrary.get(item.id()) == null) {
            status = "このケープは、まだ画像がありません: " + item.id();
            statusUntil = System.currentTimeMillis() + 4000;
            UiSounds.deny();
            return;
        }
        onPick.accept(item.id());
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        Theme.panel(g, panelX, panelY, panelW, panelH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Theme.text(g, font, "ケープを選ぶ", panelX + pad, panelY + pad, Theme.ACCENT);

        boolean show = System.currentTimeMillis() < statusUntil;
        String text = show ? status : "? は画像が未取得です。/cinema cape <id> <プレイヤー名> で取り込めます";
        Theme.text(g, font, Theme.fit(font, text, panelW - pad * 2), panelX + pad, hintY,
                show ? 0xFFE5A04B : Theme.TEXT_DIM);
    }
}