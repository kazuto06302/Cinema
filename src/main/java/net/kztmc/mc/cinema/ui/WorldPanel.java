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
 *   天気          : シングルプレイの統合サーバーを、直接操作する(コマンドは使わない)。コマンドで変えても、表示が追従する
 *   時間          : 同上。現在の時刻は、毎フレーム、表示する
 *   時間・天候の進み: 止める/進める(ゲームルール)
 *   テクスチャのアニメーション: 炎・水・溶岩・ポータルなどの再生tick(0〜20)を指定して、固定する(クライアントだけの機能)
 */
public final class WorldPanel {
    private record Label(String text, int x, int y, int color) {}

    /** 天気を変えたときの、その天気が続く長さ(tick)。ゲームのコマンドと同じ、5分 */
    private static final int WEATHER_TICKS = 6000;

    /** スライダーを動かしてから、固定をやり直すまで待つ時間(ms) */
    private static final long DEBOUNCE_MS = 300;

    /** 天気のボタンを押してから、押した天気を先に表示しておく時間(ms)。サーバーの状態が、追いつくまで */
    private static final long PENDING_WEATHER_MS = 1500;

    /** ゲームルールを、読み直す間隔(ms) */
    private static final long RULE_POLL_MS = 250;

    /** 自分が設定した時刻。クライアントの時刻は、サーバーから約1秒おきにしか届かず遅れているので、しばらくは、これを表示する */
    private static int shownTime = -1;
    private static long shownTimeUntil;

    /** テクスチャのアニメーションの再生tick(画面を開き直しても保持する) */
    private static int animationTick;

    private final List<Label> labels = new ArrayList<>();
    private final ModernButton[] weatherButtons = new ModernButton[3];
    private ModernCheckbox timeStopBox;
    private ModernCheckbox weatherStopBox;
    private Layout lay;
    private PanelHost host;
    private boolean building;   // 画面を作っている間は、スライダーなどのコールバックを、無視する
    private boolean hasServer;
    private int clockX, clockY;
    private int pendingTick = -1;
    private long pendingAt;
    private int pendingWeather = -1;
    private long pendingWeatherAt;
    private long lastPoll;

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
        timeStopBox = null;
        weatherStopBox = null;

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

        hasServer = server() != null;
        if (!hasServer) {
            labels.add(new Label(Theme.fit(host.font(), "天気と時間は、シングルプレイでだけ操作できます", w), x, y, 0xFFE5A04B));
            y += lay.th() + Theme.u(8);
        } else {
            // --- 天気 ---
            labels.add(new Label("天気", x, y, Theme.TEXT_DIM));
            y += lh;
            String[] weatherNames = {"晴れ", "雨", "雷雨"};
            int bw = (w - gap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                int mode = i;
                weatherButtons[i] = host.add(new ModernButton(x + i * (bw + gap), y, bw, lay.btnH(), weatherNames[i],
                        ModernButton.Style.DEFAULT, () -> {
                    setWeather(mode);
                    pendingWeather = mode;   // サーバーの状態が追いつくまで、押した天気を、先に表示する
                    pendingWeatherAt = System.currentTimeMillis();
                    updateWeatherButtons();
                }));
            }
            updateWeatherButtons();
            y += lay.btnH() + lay.gap() * 2;

            // --- 時間 ---
            labels.add(new Label("時間", x, y, Theme.TEXT_DIM));
            y += lh;
            clockX = x;
            clockY = y;   // 現在の時刻は、毎フレーム描く
            y += lay.th() + Theme.u(4);
            boolean overridden = shownTime >= 0 && System.currentTimeMillis() < shownTimeUntil;
            int dayTime = overridden ? shownTime : WorldTime.timeOfDay(level);
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
            y += lay.btnH() + gap;

            // --- 時間・天候の進み ---
            timeStopBox = host.add(new ModernCheckbox(x, y, w, sh, "時間の進みを止める",
                    !WorldRules.isRunning(WorldRules.Rule.TIME), on -> {
                if (!building) WorldRules.setRunning(WorldRules.Rule.TIME, !on);
            }));
            y += sh;
            weatherStopBox = host.add(new ModernCheckbox(x, y, w, sh, "天候の進みを止める",
                    !WorldRules.isRunning(WorldRules.Rule.WEATHER), on -> {
                if (!building) WorldRules.setRunning(WorldRules.Rule.WEATHER, !on);
            }));
            y += sh + lay.gap() * 2;
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

    // ================= 毎フレームの更新(コマンドなどで、外から変わったものに追従する) =================

    public void tick() {
        long now = System.currentTimeMillis();

        if (pendingTick >= 0 && now - pendingAt >= DEBOUNCE_MS) {
            TextureAnimations.fix(pendingTick);
            pendingTick = -1;
        }

        if (!hasServer) return;
        updateWeatherButtons();

        // ゲームルールは、読むのに少し手間がかかるので、間隔をあけて読む
        if (now - lastPoll >= RULE_POLL_MS) {
            lastPoll = now;
            if (timeStopBox != null) timeStopBox.setChecked(!WorldRules.isRunning(WorldRules.Rule.TIME));
            if (weatherStopBox != null) weatherStopBox.setChecked(!WorldRules.isRunning(WorldRules.Rule.WEATHER));
        }
    }

    /** 天気のボタンの選択を、いまの天気に合わせる。押した直後は、押した天気を、先に出す */
    private void updateWeatherButtons() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || weatherButtons[0] == null) return;
        int actual = currentWeather(level);
        long now = System.currentTimeMillis();
        int shown = pendingWeather >= 0 && now - pendingWeatherAt < PENDING_WEATHER_MS ? pendingWeather : actual;
        if (shown == actual) pendingWeather = -1;
        for (int i = 0; i < 3; i++) weatherButtons[i].setSelected(shown == i);
    }

    /**
     * いまの天気(0 晴れ / 1 雨 / 2 雷雨)。
     * サーバーの天気の状態を見る。クライアントの雨の量は、天気が変わってから、ゆっくり増減するので、使わない。
     */
    private static int currentWeather(ClientLevel level) {
        MinecraftServer server = server();
        if (server != null) {
            var data = server.getWeatherData();
            return data.isThundering() ? 2 : data.isRaining() ? 1 : 0;
        }
        return level.getThunderLevel(1.0f) > 0.5f ? 2 : level.getRainLevel(1.0f) > 0.2f ? 1 : 0;
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
        server.execute(() -> WorldTime.set(server, ticks));
    }

    // ================= 描画 =================

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }

        // 現在の時刻(コマンドで変えても、ここで分かる)。0 tick = 6:00
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && hasServer) {
            int ticks = WorldTime.timeOfDay(level);
            int hour = (ticks / 1000 + 6) % 24;
            int minute = (ticks % 1000) * 60 / 1000;
            Theme.text(g, host.font(), String.format("現在: %02d:%02d  (%d tick)", hour, minute, ticks),
                    clockX, clockY, Theme.TEXT);
        }
    }
}