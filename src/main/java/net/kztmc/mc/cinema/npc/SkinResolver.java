package net.kztmc.mc.cinema.npc;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/** ユーザー名からスキンを取得する。同じ名前は1回だけ問い合わせて使い回す。 */
public final class SkinResolver {
    private SkinResolver() {}

    private static final class Entry {
        volatile Supplier<PlayerSkin> skin;   // 取得できるまで null
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    /** 名前に対応するスキンの Supplier。取得できるまでは既定スキンを返す。空の名前は常に既定スキン */
    public static Supplier<PlayerSkin> forName(String name) {
        if (name == null || name.isBlank()) {
            return () -> DefaultPlayerSkin.get(new UUID(0L, 0L));
        }
        String key = name.toLowerCase(Locale.ROOT);
        UUID fallback = UUID.nameUUIDFromBytes(("cinema:" + key).getBytes(StandardCharsets.UTF_8));
        Entry entry = CACHE.computeIfAbsent(key, k -> {
            Entry e = new Entry();
            fetch(name, e);
            return e;
        });
        return () -> {
            Supplier<PlayerSkin> s = entry.skin;
            return s != null ? s.get() : DefaultPlayerSkin.get(fallback);
        };
    }

    private static void fetch(String name, Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                // 名前 -> プロフィール(スキンの情報つき)。通信が入るので別スレッドで行う
                Optional<GameProfile> profile = mc.services().profileResolver().fetchByName(name);
                if (profile.isEmpty()) {
                    Cinema.LOGGER.warn("[Cinema] player not found: {}", name);
                    return;
                }
                GameProfile resolved = profile.get();
                // スキンの読み込みの登録は描画スレッドで行う
                mc.execute(() -> entry.skin = mc.getSkinManager().createLookup(resolved, false));
            } catch (Throwable t) {
                Cinema.LOGGER.warn("[Cinema] failed to resolve skin for {}", name, t);
            }
        });
    }
}