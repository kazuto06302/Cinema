package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * ワールド設定。NPCが選択されていないときの、右パネル。
 *   天気          : シングルプレイの統合サーバーを、直接操作する(コマンドは使わない)
 *   時間          : 同上(すべての世界の時刻)
 *   テクスチャのアニメーション: 炎・水・溶岩・ポータルなどの再生tick(0〜20)を指定して、固定する(クライアントだけの機能)
 */
public final class WorldPanel {
    private record Label(String text, int x, int y, int color) {}

    /** 天気を変えたときの、その天気が続く長さ(tick)。ゲームのコマンドと同じ、5分 */
    private static final int WEATHER_TICKS = 6000;

    /** スライダーを動かしてから、固定をやり直すまで待つ時間(ms)。固定は、動きを作り直すので、少し重い */
    private static final long DEBOUNCE_MS = 300;

    /** 自分が設定した時刻。クライアントの時刻は、サーバーから約1秒おきにしか届かず遅れているので、しばらくは、これを表示する */
    private static int shownTime = -1;
    private static long shownTimeUntil;

    /** テクスチャのアニメーションの再生tick(画面を開き直しても保持する) */
    private static int animationTick;

    private final List<Label> labels = new ArrayList<>();
    private Layout lay;
    private PanelHost host;
    private boolean building;   // 画面を作っている間は、スライダーなどのコールバックを、無視する
    private int pendingTick = -1;
    private long pendingAt;

    /** 統合サーバー(シングルプレイ)。ほかのサーバーに参加しているときは null */
    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
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
            labels.add(new Label(Theme.fit(host.font(), "天気と時間は、シングルプレイでだけ操作できます", w), x, y, 0xFFE5A04B));
            y += lay.th() + Theme.u(8);
        } else {
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
                    setWeather(mode);   // 時間には、触れない
                    host.refresh();
                }).setSelected(current == i));
            }
            y += lay.btnH() + lay.gap() * 2;

            // --- 時間 ---
            labels.add(new Label("時間", x, y, Theme.TEXT_DIM));
            y += lh;
            boolean overridden = shownTime >= 0 && System.currentTimeMillis() < shownTimeUntil;
            int dayTime = overridden ? shownTime : (int) (level.getGameTime() % 24000L);
            host.add(new ModernSlider(x, y, w, sh, "時刻", "tick", 0, 24000, 10, dayTime, v -> {
                if (!building) setTime((int) v);   // 画面を作り直している間の呼び出しは、無視する
            }));
            y += sh;
            String[] presetNames = {"朝", "昼", "夕", "夜"};
            int[] presetTicks = {0, 6000, 12000, 18000};
            int pw = (w - gap * 3) / 4;
            for (int i = 0; i < 4; i++) {
                int ticks = presetTicks[i];
                host.add(new ModernButton(x + i * (pw + gap), y, pw, lay.btnH(), presetNames[i],
                        ModernButton.Style.DEFAULT, () -> {
                    setTime(ticks);
                    host.refresh();   // 作り直すと、設定した時刻が、スライダーに出る
                }));
            }
            y += lay.btnH() + lay.gap() * 2;
        }

        // --- テクスチャのアニメーション(炎・水・溶岩・ポータルなど) ---
        labels.add(new Label("テクスチャのアニメーション", x, y, Theme.TEXT_DIM));
        y += lh;
        labels.add(new Label(Theme.fit(host.font(), "炎・水・溶岩・ポータルなどを、指定のtickで止める", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(4);
        host.add(new ModernSlider(x, y, w, sh, "再生tick", "tick", 0, 20, 1,
                TextureAnimations.isFixed() ? TextureAnimations.fixedTick() : animationTick, v -> {
            animationTick = (int) v;
            if (!building && TextureAnimations.isFixed()) {   // 固定中は、スライダーが止まってから、固定し直す
                pendingTick = animationTick;
                pendingAt = System.currentTimeMillis();
            }
        }));
        y += sh;
        host.add(new ModernCheckbox(x, y, w, sh, "このtickで固定する", TextureAnimations.isFixed(), on -> {
            if (building) return;
            if (on) TextureAnimations.fix(animationTick);
            else TextureAnimations.release();
        }));
    }

    /** 毎フレーム呼ぶ。スライダーが止まってから、テクスチャのアニメーションの固定を、1回だけやり直す */
    public void tick() {
        if (pendingTick >= 0 && System.currentTimeMillis() - pendingAt >= DEBOUNCE_MS) {
            TextureAnimations.fix(pendingTick);
            pendingTick = -1;
        }
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
        shownTime = ticks;
        shownTimeUntil = System.currentTimeMillis() + 2000;
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

    // ================= 描画 =================

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
    }
}