package net.kztmc.mc.cinema.npc;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/**
 * NPCごとの文字の設定(ネームタグの表示名・BelowName・ケープの持ち主)。
 * NpcData の保存形式(コーデック)は変えずに、別のファイル(config/cinema/<ワールド名>.texts.json)へ保存する。
 */
public final class NpcTexts {
    private NpcTexts() {}

    public static final String NAME_TAG = "nametag";
    public static final String BELOW_NAME = "belowname";
    public static final String CAPE_OWNER = "cape";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
    private static final Map<UUID, Map<String, String>> TEXTS = new HashMap<>();

    /** 空なら、設定なし("") */
    public static String get(UUID id, String key) {
        return TEXTS.getOrDefault(id, Map.of()).getOrDefault(key, "");
    }

    public static void set(UUID id, String key, String value) {
        String text = value == null ? "" : value;
        Map<String, String> map = TEXTS.computeIfAbsent(id, k -> new HashMap<>());
        if (text.isEmpty()) map.remove(key);
        else map.put(key, text);
        NpcManager.markDirty();
    }

    /** 複製: 設定を、新しいNPCへ写す */
    public static void copy(UUID from, UUID to) {
        Map<String, String> map = TEXTS.get(from);
        if (map != null) TEXTS.put(to, new HashMap<>(map));
    }

    public static void remove(UUID id) {
        TEXTS.remove(id);
    }

    public static void clear() {
        TEXTS.clear();
    }

    private static Path file(String worldKey) {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema").resolve(worldKey + ".texts.json");
    }

    public static void load(String worldKey) {
        TEXTS.clear();
        Path path = file(worldKey);
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Map<String, String>> loaded = GSON.fromJson(reader, FILE_TYPE);
            if (loaded == null) return;
            loaded.forEach((id, map) -> {
                try {
                    TEXTS.put(UUID.fromString(id), new HashMap<>(map));
                } catch (IllegalArgumentException ignored) {
                    // 壊れたIDは、飛ばす
                }
            });
        } catch (IOException | RuntimeException ex) {
            Cinema.LOGGER.warn("[Cinema] could not read {}", path, ex);
        }
    }

    public static void save(String worldKey) {
        Path path = file(worldKey);
        try {
            Files.createDirectories(path.getParent());
            Map<String, Map<String, String>> out = new HashMap<>();
            TEXTS.forEach((id, map) -> {
                if (!map.isEmpty()) out.put(id.toString(), map);
            });
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(out, FILE_TYPE, writer);
            }
        } catch (IOException | RuntimeException ex) {
            Cinema.LOGGER.warn("[Cinema] could not write {}", path, ex);
        }
    }
}