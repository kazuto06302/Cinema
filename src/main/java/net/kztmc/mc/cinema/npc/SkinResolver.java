package net.kztmc.mc.cinema.npc;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/** ユーザー名からスキンを取得する。同じ名前は使い回す。失敗したら、間を空けて自動でやり直す。 */
public final class SkinResolver {
    private SkinResolver() {}

    /** 取得を試す回数の上限(最初の1回を含む) */
    private static final int MAX_ATTEMPTS = 4;
    /** 失敗してから、次を試すまでの待ち時間(秒)。1回目の失敗のあとは [0]、2回目は [1]… */
    private static final long[] RETRY_SECONDS = {10, 30, 60};
    /** スキンを要求してから、本物が届いたかを確かめるまでの時間(秒) */
    private static final long VERIFY_SECONDS = 12;

    /** 通信は、このスレッドで1件ずつ行う(レート制限に当たりにくくする) */
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Cinema-SkinResolver");
        t.setDaemon(true);
        return t;
    });

    private static final class Entry {
        final String name;
        final UUID fallbackId;
        volatile Supplier<PlayerSkin> skin;   // 取得できるまで null
        volatile GameProfile profile;
        int attempts;                         // EXECUTOR のスレッドだけで触る
        boolean gaveUp;                       // 同上

        Entry(String name) {
            this.name = name;
            this.fallbackId = UUID.nameUUIDFromBytes(("cinema:" + name.toLowerCase(Locale.ROOT))
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    /** 名前に対応するスキンの Supplier。取得できるまでは既定スキンを返す。空の名前は常に既定スキン */
    public static Supplier<PlayerSkin> forName(String name) {
        if (name == null || name.isBlank()) {
            return () -> DefaultPlayerSkin.get(new UUID(0L, 0L));
        }
        Entry entry = CACHE.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
            Entry e = new Entry(name);
            EXECUTOR.execute(() -> attempt(e));
            return e;
        });
        PlayerSkin fallback = DefaultPlayerSkin.get(entry.fallbackId);
        return () -> {
            Supplier<PlayerSkin> s = entry.skin;
            return s != null ? s.get() : fallback;
        };
    }

    /** あきらめたもの(上限まで失敗したもの)を、もう一度試す。/cinema skins から呼ぶ */
    public static void retryAll() {
        EXECUTOR.execute(() -> {
            for (Entry e : CACHE.values()) {
                if (!e.gaveUp) continue;
                e.gaveUp = false;
                e.attempts = 0;
                EXECUTOR.execute(() -> attempt(e));
            }
        });
    }

    // ---- 取得(EXECUTOR のスレッドで動く) ----

    private static void attempt(Entry e) {
        e.attempts++;
        Minecraft mc = Minecraft.getInstance();
        try {
            // 名前 -> プロフィール(スキンの情報つき)。通信が入る
            Optional<GameProfile> found = mc.services().profileResolver().fetchByName(e.name);
            if (found.isEmpty()) {
                retryLater(e, "profile not found");
                return;
            }
            GameProfile resolved = found.get();
            e.profile = resolved;
            // スキンの読み込みの登録は描画スレッドで行う
            mc.execute(() -> {
                e.skin = mc.getSkinManager().createLookup(resolved, false);
                EXECUTOR.schedule(() -> verify(e), VERIFY_SECONDS, TimeUnit.SECONDS);
            });
        } catch (Throwable t) {
            retryLater(e, t.toString());
        }
    }

    /** 本物のスキンが届いたか確かめる。まだ既定スキンのままなら、読み込みに失敗したとみなして、やり直す */
    private static void verify(Entry e) {
        Supplier<PlayerSkin> skin = e.skin;
        GameProfile profile = e.profile;
        if (skin == null || profile == null) return;
        if (!skin.get().equals(DefaultPlayerSkin.get(profile))) return;   // 届いている
        retryLater(e, "skin did not arrive (still the default skin)");
    }

    private static void retryLater(Entry e, String reason) {
        if (e.attempts >= MAX_ATTEMPTS) {
            e.gaveUp = true;
            Cinema.LOGGER.warn("[Cinema] gave up the skin of {} after {} attempts: {}", e.name, e.attempts, reason);
            return;
        }
        long wait = RETRY_SECONDS[Math.min(e.attempts - 1, RETRY_SECONDS.length - 1)];
        Cinema.LOGGER.info("[Cinema] skin of {} is not ready ({}). retrying in {}s ({}/{})",
                e.name, reason, wait, e.attempts, MAX_ATTEMPTS);
        EXECUTOR.schedule(() -> attempt(e), wait, TimeUnit.SECONDS);
    }
}