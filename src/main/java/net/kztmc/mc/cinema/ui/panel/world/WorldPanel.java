package net.kztmc.mc.cinema.ui.panel.world;

import java.util.ArrayList;
import java.util.List;

import net.kztmc.mc.cinema.ui.core.Layout;
import net.kztmc.mc.cinema.ui.core.PanelHost;
import net.kztmc.mc.cinema.ui.panel.Theme;
import net.kztmc.mc.cinema.ui.widget.ModernButton;
import net.kztmc.mc.cinema.ui.widget.ModernCheckbox;
import net.kztmc.mc.cinema.ui.widget.ModernSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * ワールド設定。NPCが選択されていないときの、右パネル。
 *   時間・天候: クライアント側の上書き(サーバー・ゲームルールには触れない。どのサーバーでも使える)
 *   テクスチャのアニメーション: 再生tickで固定(設定は保存する)
 */
public final class WorldPanel {
    private record Label(String text, int x, int y, int color) {}

    private final List<Label> labels = new ArrayList<>();
    private Layout lay;
    private PanelHost host;
    private boolean building;
    private int clockX, clockY;

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

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        int x = lay.rightX() + lay.pad();
        int w = lay.panelW() - lay.pad() * 2;
        int y = lay.panelY() + Theme.u(8);
        int lh = lay.th() + Theme.u(3);
        int sh = lay.sliderH();
        int gap = Math.max(1, Theme.u(3));

        labels.add(new Label("ワールド設定", x, y, Theme.ACCENT));
        y += lay.th() + Theme.u(3);
        labels.add(new Label(Theme.fit(host.font(), "NPCを選ぶと、そのNPCの編集になります", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(3);
        labels.add(new Label(Theme.fit(host.font(), "時間・天候は、自分の画面だけの上書きです", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(8);

        // --- 天気 ---
        labels.add(new Label("天気", x, y, Theme.TEXT_DIM));
        y += lh;
        host.add(new ModernCheckbox(x, y, w, sh, "天気を上書きする", ClientOverrides.weatherOn, on -> {
            if (building) return;
            ClientOverrides.weatherOn = on;
            WorldSettings.save();
            host.refresh();
        }));
        y += sh;
        String[] weatherNames = {"晴れ", "雨", "雷雨"};
        int bw = (w - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int mode = i;
            ModernButton b = host.add(new ModernButton(x + i * (bw + gap), y, bw, lay.btnH(), weatherNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                ClientOverrides.weather = mode;
                WorldSettings.save();
                host.refresh();
            }));
            b.setSelected(ClientOverrides.weatherOn && ClientOverrides.weather == i);
            b.active = ClientOverrides.weatherOn;
        }
        y += lay.btnH() + lay.gap() * 2;

        // --- 時間 ---
        labels.add(new Label("時間", x, y, Theme.TEXT_DIM));
        y += lh;
        clockX = x;
        clockY = y;   // 実際の時刻は、毎フレーム描く
        y += lay.th() + Theme.u(4);
        host.add(new ModernCheckbox(x, y, w, sh, "時間を上書きする", ClientOverrides.timeOn, on -> {
            if (building) return;
            ClientOverrides.timeOn = on;   // 設定値(timeTicks)には触れない
            WorldSettings.save();
            host.refresh();
        }));
        y += sh;
        host.add(new ModernSlider(x, y, w, sh, "時刻", "tick", 0, 24000, 10, ClientOverrides.timeTicks, v -> {
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
            host.add(new ModernButton(x + i * (pw + gap), y, pw, lay.btnH(), presetNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                ClientOverrides.timeTicks = ticks;   // こちらも、チェックに関係なく押せる
                WorldSettings.save();
                host.refresh();
            }));
        }
        y += lay.btnH() + lay.gap() * 2;

        // --- テクスチャのアニメーション(炎・水・溶岩・ポータルなど) ---
        labels.add(new Label("テクスチャのアニメーション", x, y, Theme.TEXT_DIM));
        y += lh;
        labels.add(new Label(Theme.fit(host.font(), "炎・水・溶岩・ポータルなどを、指定のtickで止める", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(4);
        host.add(new ModernSlider(x, y, w, sh, "再生tick", "tick", 0, 20, 1,
                TextureAnimations.isFixed() ? TextureAnimations.fixedTick() : WorldSettings.textureTick, v -> {
            if (building) return;
            int tick = (int) v;
            if (tick == WorldSettings.textureTick && TextureAnimations.isFixed()) return;
            WorldSettings.textureTick = tick;
            if (TextureAnimations.isFixed()) TextureAnimations.fix(tick);
            WorldSettings.save();
        }));
        y += sh;
        host.add(new ModernCheckbox(x, y, w, sh, "このtickで固定する", TextureAnimations.isFixed(), on -> {
            if (building) return;
            if (on) TextureAnimations.fix(WorldSettings.textureTick);
            else TextureAnimations.release();
            WorldSettings.textureFixed = on;
            WorldSettings.save();
        }));

        y += sh + lay.gap();
        host.add(new ModernButton(x, y, w, lay.btnH(), "描画する項目を選ぶ...", ModernButton.Style.DEFAULT,
                () -> Minecraft.getInstance().gui.setScreen(new RenderTogglesScreen(host.screen()))));
    }

    /** 毎フレーム呼ばれる。いまは、追従するものがない */
    public void tick() {
        WorldSettings.flush();
    }

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
        // 実際の(上書き前の)時刻。0 tick = 6:00
        int ticks = (int) Math.floorMod(ClientOverrides.realTime, 24000L);
        int hour = (ticks / 1000 + 6) % 24;
        int minute = (ticks % 1000) * 60 / 1000;
        Theme.text(g, host.font(), String.format("実際: %02d:%02d  (%d tick)", hour, minute, ticks),
                clockX, clockY, Theme.TEXT);
    }
}