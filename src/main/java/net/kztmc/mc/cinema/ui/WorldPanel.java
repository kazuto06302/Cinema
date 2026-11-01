package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;

/**
 * ワールド設定。NPCが選択されていないときの、右パネル。
 * コマンドは使わず、シングルプレイの統合サーバーを、直接操作する(ほかのサーバーに参加しているときは、操作できない)。
 *   天気          : サーバーの天気を、直接設定する
 *   時間          : すべての世界の時刻を、直接設定する
 *   世界の進み具合: 世界を止めた状態から、指定したtickだけ進めて、また止める(ServerTickRateManager)
 */
public final class WorldPanel {
    private record Label(String text, int x, int y, int color) {}

    /** 天気を変えたときの、その天気が続く長さ(tick)。ゲームのコマンドと同じ、5分 */
    private static final int WEATHER_TICKS = 6000;

    /** 「進める」tick の数(画面を開き直しても保持する) */
    private static int stepTicks = 20;

    private final List<Label> labels = new ArrayList<>();
    private Layout lay;
    private PanelHost host;
    private int stateY;

    /** 統合サーバー(シングルプレイ)。ほかのサーバーに参加しているときは null */
    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    public void build(Layout lay, PanelHost host) {
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
        y += lay.th() + Theme.u(8);

        if (server() == null) {
            labels.add(new Label(Theme.fit(host.font(), "シングルプレイ(統合サーバー)でだけ", w), x, y, 0xFFE5A04B));
            y += lay.th() + Theme.u(3);
            labels.add(new Label(Theme.fit(host.font(), "ワールドを操作できます", w), x, y, 0xFFE5A04B));
            return;
        }

        // --- 天気 ---
        labels.add(new Label("天気", x, y, Theme.TEXT_DIM));
        y += lh;
        int current = level.isThundering() ? 2 : level.isRaining() ? 1 : 0;
        String[] weatherNames = {"晴れ", "雨", "雷雨"};
        int bw = (w - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int mode = i;
            host.add(new ModernButton(x + i * (bw + gap), y, bw, lay.btnH(), weatherNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                setWeather(mode);
                host.refresh();
            }).setSelected(current == i));
        }
        y += lay.btnH() + lay.gap() * 2;

        // --- 時間 ---
        labels.add(new Label("時間", x, y, Theme.TEXT_DIM));
        y += lh;
        int dayTime = (int) (level.getGameTime() % 24000L);
        host.add(new ModernSlider(x, y, w, sh, "時刻", "tick", 0, 24000, 10, dayTime, v -> setTime((int) v)));
        y += sh;
        String[] presetNames = {"朝", "昼", "夕", "夜"};
        int[] presetTicks = {0, 6000, 12000, 18000};
        int pw = (w - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int ticks = presetTicks[i];
            host.add(new ModernButton(x + i * (pw + gap), y, pw, lay.btnH(), presetNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                setTime(ticks);
                host.refresh();
            }));
        }
        y += lay.btnH() + lay.gap() * 2;

        // --- 世界の進み具合: 指定したtickだけ進めて、止める ---
        labels.add(new Label("世界の進み具合", x, y, Theme.TEXT_DIM));
        y += lh;
        stateY = y;   // 「停止中 / 進行中」は、毎フレーム、いまの状態を描く
        y += lay.th() + Theme.u(4);

        host.add(new ModernSlider(x, y, w, sh, "進める", "tick", 1, 200, 1, stepTicks, v -> stepTicks = (int) v));
        y += sh;
        host.add(new ModernButton(x, y, w, lay.btnH(), "進めて止める", ModernButton.Style.PRIMARY,
                () -> stepAndStop(stepTicks)));
        y += lay.btnH() + gap;

        int half = (w - gap) / 2;
        host.add(new ModernButton(x, y, half, lay.btnH(), "停止", ModernButton.Style.DEFAULT, () -> setFrozen(true)));
        host.add(new ModernButton(x + half + gap, y, half, lay.btnH(), "再開", ModernButton.Style.DEFAULT,
                () -> setFrozen(false)));
        y += lay.btnH() + gap;
        labels.add(new Label(Theme.fit(host.font(), "20 tick = 1秒", w), x, y, Theme.TEXT_DIM));
    }

    // ================= サーバーの操作(サーバーのスレッドで実行する) =================

    private static void setWeather(int mode) {
        MinecraftServer server = server();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();   // 天気は、オーバーワールドが持つ
            switch (mode) {
                case 0 -> level.getServer().setWeatherParameters(WEATHER_TICKS, 0, false, false);
                case 1 -> level.getServer().setWeatherParameters(0, WEATHER_TICKS, true, false);
                default -> level.getServer().setWeatherParameters(0, WEATHER_TICKS, true, true);
            }
        });
    }

    private static void setTime(int ticks) {
        MinecraftServer server = server();
        if (server == null) return;
        server.execute(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                var clock = level.dimensionType().defaultClock();
                if (clock.isEmpty()) continue;
                server.clockManager().setTotalTicks(clock.get(), ticks);
            }
        });
    }

    /** 世界を止めて、指定したtickだけ進める。進み終わったら、止まったままになる */
    private static void stepAndStop(int ticks) {
        MinecraftServer server = server();
        if (server == null) return;
        server.execute(() -> {
            ServerTickRateManager manager = server.tickRateManager();
            if (!manager.isFrozen()) manager.setFrozen(true);
            manager.stepGameIfPaused(ticks);
        });
    }

    private static void setFrozen(boolean frozen) {
        MinecraftServer server = server();
        if (server == null) return;
        server.execute(() -> server.tickRateManager().setFrozen(frozen));
    }

    // ================= 描画 =================

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
        // 世界の状態(クライアント側の写し)。毎フレーム、いまの状態を出す
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && server() != null) {
            boolean frozen = level.tickRateManager().isFrozen();
            Theme.text(g, host.font(), frozen ? "停止中" : "進行中", lay.rightX() + lay.pad(), stateY,
                    frozen ? 0xFFE5A04B : 0xFF57AB5A);
        }
    }
}