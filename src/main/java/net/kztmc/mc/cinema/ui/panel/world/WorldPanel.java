package net.kztmc.mc.cinema.ui.panel.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import net.kztmc.mc.cinema.ui.core.Layout;
import net.kztmc.mc.cinema.ui.core.PanelHost;
import net.kztmc.mc.cinema.ui.core.ScrollArea;
import net.kztmc.mc.cinema.ui.core.ScrollPanel;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernCheckbox;
import net.kztmc.mc.cinema.ui.widget.ModernSlider;
import net.kztmc.mc.cinema.ui.widget.SectionHeaderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * ワールド設定。NPCが選択されていないときの、右パネル。
 * 畳める見出しつきのセクションを縦に並べる。上のタイトルは固定で、セクションの部分は、表示範囲で切り取って、スクロールする。
 * セクションを増やすときは、buildContent に section(...) を足す。部品は scroll.add、文字は scrollLabels に足す。
 */
public final class WorldPanel {
    private record Label(String text, int x, int y, int color) {}

    /** 畳んでいるセクション(画面を開き直しても保持する) */
    private static final Set<String> collapsed = new HashSet<>();

    private static final String[] MOON_NAMES = {"満月", "欠けていく(大)", "下弦", "欠けていく(小)",
            "新月", "満ちていく(小)", "上弦", "満ちていく(大)"};

    private final ScrollArea scroll = new ScrollArea();
    private final List<Label> labels = new ArrayList<>();         // 固定(タイトルと説明)
    private final List<Label> scrollLabels = new ArrayList<>();   // スクロールする
    private final List<int[]> spans = new ArrayList<>();          // 各セクションの上と下(スクロール前の座標)
    private Layout lay;
    private PanelHost host;
    private boolean building;
    private int baseX, baseW;      // 見出しとカードの位置(インデントなし)
    private int x, w;              // 中身の位置(インデントあり。各セクションは、これを使う)
    private int clockX, clockY = -1;

    /** 画面が、スクロールの処理を任せる相手 */
    public ScrollPanel scroll() {
        return scroll;
    }

    public void build(Layout lay, PanelHost host) {
        building = true;
        try {
            buildContent(lay, host);
        } finally {
            building = false;
        }
    }

    private void buildContent(Layout lay, PanelHost host) {
        this.lay = lay;
        this.host = host;
        labels.clear();
        scrollLabels.clear();
        spans.clear();
        clockY = -1;
        scroll.begin(host, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        int fullX = lay.rightX() + lay.pad();
        int fullW = lay.panelW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);

        labels.add(new Label("ワールド設定", fullX, y, Theme.ACCENT));
        y += lay.th() + Theme.u(3);
        labels.add(new Label(Theme.fit(host.font(), "NPCを選ぶと、そのNPCの編集になります", fullW), fullX, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(3);
        labels.add(new Label(Theme.fit(host.font(), "時間・天候は、自分の画面だけの上書きです", fullW), fullX, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(8);

        // ここから下が、スクロールする部分
        int bandTop = y;
        int bandBottom = lay.panelY() + lay.panelH() - lay.pad();
        scroll.setBand(bandTop, bandBottom);

        int barW = Theme.u(5);
        baseX = fullX;
        baseW = fullW - barW - Theme.u(2);
        x = baseX;
        w = baseW;
        y += Theme.u(3);

        // セクション(増やすときは、ここに1行足して、sectionXxx メソッドを書く)
        y = section("weather", "天気", this::sectionWeather, y);
        y = section("time", "時間", this::sectionTime, y);
        y = section("moon", "月", this::sectionMoon, y);
        y = section("texture", "アニメーションの固定", this::sectionTexture, y);
        y = section("render", "描画", this::sectionRender, y);

        scroll.end(y, fullX + fullW - barW, barW);
    }

    /**
     * 見出しと中身を組み立てて、カードの範囲を記録する。畳んでいるときは、見出しだけ。
     * body は、中身の先頭の y を受け取り、中身の下端の y を返す(下の余白は、ここで足す)。
     */
    private int section(String key, String title, IntUnaryOperator body, int y) {
        int top = y;
        int headerH = SectionHeaderButton.height();
        scroll.add(new SectionHeaderButton(baseX, y, baseW, headerH, title, () -> collapsed.contains(key), () -> {
            if (!collapsed.remove(key)) collapsed.add(key);
            host.refresh();
        }));
        y += headerH;

        if (!collapsed.contains(key)) {
            int indent = Theme.u(4);
            x = baseX + indent;
            w = baseW - indent;
            y = body.applyAsInt(y + Theme.u(2));
            x = baseX;
            w = baseW;
        }
        spans.add(new int[] {top, y});
        return y + Theme.u(9);
    }

    // ================= セクション =================

    private int sectionWeather(int y) {
        int sh = lay.sliderH();
        int gap = Math.max(1, Theme.u(3));

        scroll.add(new ModernCheckbox(x, y, w, sh, "天気を上書きする", ClientOverrides.weatherOn, on -> {
            if (building) return;
            ClientOverrides.weatherOn = on;
            WorldSettings.save();
            host.refresh();
        }));
        y += sh;

        String[] names = {"晴れ", "雨", "雷雨"};
        int bw = (w - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int mode = i;
            ModernButton b = scroll.add(new ModernButton(x + i * (bw + gap), y, bw, lay.btnH(), names[i],
                    ModernButton.Style.DEFAULT, () -> {
                ClientOverrides.weather = mode;
                WorldSettings.save();
                host.refresh();
            }));
            b.setSelected(ClientOverrides.weatherOn && ClientOverrides.weather == i);
            b.active = ClientOverrides.weatherOn;
        }
        return y + lay.btnH();
    }

    private int sectionTime(int y) {
        int sh = lay.sliderH();
        int gap = Math.max(1, Theme.u(3));

        clockX = x;
        clockY = y;   // 実際の時刻は、毎フレーム描く
        y += lay.th() + Theme.u(4);

        scroll.add(new ModernCheckbox(x, y, w, sh, "時間を上書きする", ClientOverrides.timeOn, on -> {
            if (building) return;
            ClientOverrides.timeOn = on;   // 設定値(timeTicks)には触れない
            WorldSettings.save();
            host.refresh();
        }));
        y += sh;

        scroll.add(new ModernSlider(x, y, w, sh, "時刻", "tick", 0, 24000, 10, ClientOverrides.timeTicks, v -> {
            if (building) return;
            ClientOverrides.timeTicks = (int) v;   // チェックに関係なく、設定値を変える
            WorldSettings.markDirty();
        }));
        y += sh;

        String[] presetNames = {"朝", "昼", "夕", "夜"};
        int[] presetTicks = {0, 6000, 12000, 18000};
        int pw = (w - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int ticks = presetTicks[i];
            scroll.add(new ModernButton(x + i * (pw + gap), y, pw, lay.btnH(), presetNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                ClientOverrides.timeTicks = ticks;   // こちらも、チェックに関係なく押せる
                WorldSettings.save();
                host.refresh();
            }));
        }
        return y + lay.btnH();
    }

    private int sectionMoon(int y) {
        int sh = lay.sliderH();

        scroll.add(new ModernCheckbox(x, y, w, sh, "月の形を上書きする", ClientOverrides.moonOn, on -> {
            if (building) return;
            ClientOverrides.moonOn = on;
            WorldSettings.save();
            host.refresh();
        }));
        y += sh;

        scroll.add(new ModernSlider(x, y, w, sh, "月の形", "", 0, 7, ClientOverrides.moonPhase, v -> {
            if (building) return;
            ClientOverrides.moonPhase = (int) v;   // チェックに関係なく、設定値を変える
            WorldSettings.markDirty();
        }).setValueFormatter(v -> MOON_NAMES[Math.max(0, Math.min(7, (int) Math.round(v)))]).setDefault(0));
        return y + sh;
    }

    private int sectionTexture(int y) {
        int sh = lay.sliderH();

        scrollLabels.add(new Label(Theme.fit(host.font(),
                "炎・水・溶岩・ポータル・パーティクルなどを、指定のtickで止める", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(4);

        scroll.add(new ModernSlider(x, y, w, sh, "再生tick", "tick", 0, 20, 1,
                TextureAnimations.isFixed() ? TextureAnimations.fixedTick() : WorldSettings.textureTick, v -> {
            if (building) return;
            int tick = (int) v;
            if (tick == WorldSettings.textureTick && TextureAnimations.isFixed()) return;
            WorldSettings.textureTick = tick;
            if (TextureAnimations.isFixed()) TextureAnimations.fix(tick);
            WorldSettings.save();
        }));
        y += sh;

        scroll.add(new ModernCheckbox(x, y, w, sh, "このtickで固定する", TextureAnimations.isFixed(), on -> {
            if (building) return;
            if (on) TextureAnimations.fix(WorldSettings.textureTick);
            else TextureAnimations.release();
            WorldSettings.textureFixed = on;
            WorldSettings.save();
        }));
        return y + sh;
    }

    private int sectionRender(int y) {
        scroll.add(new ModernButton(x, y, w, lay.btnH(), "描画する項目を選ぶ...", ModernButton.Style.DEFAULT,
                () -> Minecraft.getInstance().gui.setScreen(new RenderTogglesScreen(host.screen()))));
        return y + lay.btnH();
    }

    // ================= 描画 =================

    /** 毎フレーム呼ばれる */
    public void tick() {
        WorldSettings.flush();
    }

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }

        // スクロールする部分(カード・文字・時計)は、表示範囲で切り取って、スクロール量だけずらして描く
        int off = scroll.offset();
        int padV = Theme.u(2);
        int padH = Theme.u(3);
        g.enableScissor(lay.rightX(), scroll.top(), lay.rightX() + lay.panelW(), scroll.bottom());

        for (int[] s : spans) {
            int top = s[0] - off - padV;
            Theme.roundRect(g, baseX - padH, top, baseW + padH * 2, s[1] - off - top, Theme.SECTION_CARD);
        }
        for (Label l : scrollLabels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y() - off, l.color());
        }

        // 実際の(上書き前の)時刻。0 tick = 6:00。時間のセクションを開いているときだけ
        if (clockY >= 0) {
            int ticks = (int) Math.floorMod(ClientOverrides.realTime, 24000L);
            int hour = (ticks / 1000 + 6) % 24;
            int minute = (ticks % 1000) * 60 / 1000;
            Theme.text(g, host.font(), String.format("実際: %02d:%02d  (%d tick)", hour, minute, ticks),
                    clockX, clockY - off, Theme.TEXT);
        }
        g.disableScissor();
    }
}