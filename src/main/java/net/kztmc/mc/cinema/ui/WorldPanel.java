package net.kztmc.mc.cinema.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * ワールド設定。NPCが選択されていないときの、右パネル。
 * ゲームのコマンドを送って、実際のワールドへ反映する(OPの権限が必要)。
 *   天気          : /weather clear | rain | thunder
 *   時間          : /time set <tick>
 *   世界の進み具合: /tick rate <1〜20>、0 は /tick freeze(NPCは、もともと止まっているので、影響しない)
 */
public final class WorldPanel {
    private record Label(String text, int x, int y, int color) {}

    /** スライダーを動かしてから、コマンドを送るまで待つ時間(ms)。チャットが、コマンドの結果で埋まらないようにする */
    private static final long DEBOUNCE_MS = 300;

    private final List<Label> labels = new ArrayList<>();
    private Layout lay;
    private PanelHost host;

    private int pendingTime = -1;
    private int pendingRate = -1;
    private long pendingTimeAt;
    private long pendingRateAt;

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

        labels.add(new Label("ワールド設定", x, y, Theme.ACCENT));
        y += lay.th() + Theme.u(3);
        labels.add(new Label(Theme.fit(host.font(), "NPCを選ぶと、そのNPCの編集になります", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(3);
        labels.add(new Label(Theme.fit(host.font(), "※ 変更には、OPの権限が必要です", w), x, y, Theme.TEXT_DIM));
        y += lay.th() + Theme.u(8);

        // --- 天気 ---
        labels.add(new Label("天気", x, y, Theme.TEXT_DIM));
        y += lh;
        int current = level.isThundering() ? 2 : level.isRaining() ? 1 : 0;
        String[] weatherNames = {"晴れ", "雨", "雷雨"};
        String[] weatherCommands = {"weather clear", "weather rain", "weather thunder"};
        int gap = Math.max(1, Theme.u(3));
        int bw = (w - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            String command = weatherCommands[i];
            host.add(new ModernButton(x + i * (bw + gap), y, bw, lay.btnH(), weatherNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                run(command);
                host.refresh();
            }).setSelected(current == i));
        }
        y += lay.btnH() + lay.gap() * 2;

        // --- 時間 ---
        labels.add(new Label("時間", x, y, Theme.TEXT_DIM));
        y += lh;
        int dayTime = (int) (level.getGameTime() % 24000L);
        host.add(new ModernSlider(x, y, w, sh, "時刻", "tick", 0, 24000, 10, dayTime, v -> {
            pendingTime = (int) v;
            pendingTimeAt = System.currentTimeMillis();
        }));
        y += sh;
        String[] presetNames = {"朝", "昼", "夕", "夜"};
        int[] presetTicks = {0, 6000, 12000, 18000};
        int pw = (w - gap * 3) / 4;
        for (int i = 0; i < 4; i++) {
            int ticks = presetTicks[i];
            host.add(new ModernButton(x + i * (pw + gap), y, pw, lay.btnH(), presetNames[i],
                    ModernButton.Style.DEFAULT, () -> {
                run("time set " + ticks);
                host.refresh();
            }));
        }
        y += lay.btnH() + lay.gap() * 2;

        // --- 世界の進み具合 ---
        labels.add(new Label("世界の進み具合", x, y, Theme.TEXT_DIM));
        y += lh;
        var ticking = level.tickRateManager();
        int rate = ticking.isFrozen() ? 0 : Math.max(1, Math.min(20, Math.round(ticking.tickrate())));
        host.add(new ModernSlider(x, y, w, sh, "進み具合", "tick/秒", 0, 20, 1, rate, v -> {
            pendingRate = (int) v;
            pendingRateAt = System.currentTimeMillis();
        }));
        y += sh;
        labels.add(new Label(Theme.fit(host.font(), "20 = ふつう / 0 = 世界の時間を止める", w), x, y + Theme.u(2), Theme.TEXT_DIM));
    }

    /** 毎フレーム呼ぶ。スライダーが止まってから、コマンドを1回だけ送る */
    public void tick() {
        long now = System.currentTimeMillis();
        if (pendingTime >= 0 && now - pendingTimeAt >= DEBOUNCE_MS) {
            run("time set " + pendingTime);
            pendingTime = -1;
        }
        if (pendingRate >= 0 && now - pendingRateAt >= DEBOUNCE_MS) {
            applyRate(pendingRate);
            pendingRate = -1;
        }
    }

    private static void applyRate(int rate) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        var ticking = level.tickRateManager();
        if (rate <= 0) {
            if (!ticking.isFrozen()) run("tick freeze");
        } else {
            if (ticking.isFrozen()) run("tick unfreeze");
            if (Math.round(ticking.tickrate()) != rate) run("tick rate " + rate);
        }
    }

    private static void run(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(command);
    }

    public void extractBackground(GuiGraphicsExtractor g) {
        Theme.panel(g, lay.rightX(), lay.panelY(), lay.panelW(), lay.panelH());
        for (Label l : labels) {
            Theme.text(g, host.font(), l.text(), l.x(), l.y(), l.color());
        }
    }
}