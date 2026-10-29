package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.IconStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 下パネル(devモードの「モブ追加」タブだけ): モブアイコンの大きさ・向きの調整。 */
public final class TunerPanel {
    private record Label(String text, int x, int y, int color) {}

    private final List<Label> labels = new ArrayList<>();
    private int x0, y0, w0, h0;
    private PanelHost host;

    public void build(Layout lay, PanelHost host) {
        this.host = host;
        labels.clear();

        w0 = Theme.u(170);
        int titleH = lay.th() + Theme.u(5);
        h0 = lay.pad() * 2 + titleH + lay.sliderH() * 6 + lay.gap() + lay.btnH();
        x0 = (host.screen().width - w0) / 2;
        y0 = host.screen().height - lay.m() - h0;

        int x = x0 + lay.pad();
        int w = w0 - lay.pad() * 2;
        int y = y0 + lay.pad();
        int sh = lay.sliderH();

        String title = "アイコン調整" + (CinemaState.tuneId == null ? "" : ": " + CinemaState.tuneName);
        labels.add(new Label(Theme.fit(host.font(), title, w), x, y, Theme.ACCENT));
        y += titleH;

        // 全モブ共通の大きさ
        host.add(new ModernSlider(x, y, w, sh, "全体の大きさ", "%", 30, 120,
                Math.round(MobIcons.getGlobalFit() * 100f),
                v -> MobIcons.setGlobalFit((float) v / 100f)));
        y += sh;

        if (CinemaState.tuneId == null) {
            labels.add(new Label("カードをクリックして選択", x, y + Theme.u(3), Theme.TEXT_DIM));
            return;
        }

        String id = CinemaState.tuneId;
        IconStyle s = MobIcons.styleOf(id);

        host.add(new ModernSlider(x, y, w, sh, "このモブの大きさ", "%", 30, 150, Math.round(s.fit() * 100f),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withFit((float) v / 100f))));
        y += sh;
        host.add(new ModernSlider(x, y, w, sh, "横回転", "°", -180, 180, s.yaw(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withYaw((float) v))));
        y += sh;
        host.add(new ModernSlider(x, y, w, sh, "縦回転", "°", -90, 90, s.pitch(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withPitch((float) v))));
        y += sh;
        host.add(new ModernSlider(x, y, w, sh, "傾き(Z回転)", "°", -180, 180, s.roll(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withRoll((float) v))));
        y += sh;
        host.add(new ModernSlider(x, y, w, sh, "高さ", "%", -100, 100, s.height(),
                v -> MobIcons.setDebugStyle(id, MobIcons.styleOf(id).withHeight((float) v))));
        y += sh + lay.gap();

        int half = (w - lay.gap()) / 2;
        host.add(new ModernButton(x, y, half, lay.btnH(), "行をコピー", ModernButton.Style.PRIMARY, () -> {
            String line = MobIcons.styleLine(id);
            Minecraft.getInstance().keyboardHandler.setClipboard(line);
            Cinema.LOGGER.info("[Cinema] {}   (FIT={})", line, MobIcons.getGlobalFit());
        }));
        host.add(new ModernButton(x + half + lay.gap(), y, half, lay.btnH(), "リセット",
                ModernButton.Style.DEFAULT, () -> {
            MobIcons.clearDebugStyle(id);
            host.refresh();
        }));
    }

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, x0, y0, w0, h0);
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
    }
}