package net.kztmc.mc.cinema.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.LevelResource;

/** config/cinema/<world-key>.json にNPC定義を保存/読込する。 */
public final class NpcStorage {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NpcStorage() {}

    /** シングル: セーブフォルダ名 / マルチ: サーバーアドレス */
    public static String worldKey(Minecraft mc) {
        String raw;
        if (mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
            raw = "sp_" + root.getFileName();
        } else if (mc.getCurrentServer() != null) {
            raw = "mp_" + mc.getCurrentServer().ip;
        } else {
            raw = "unknown";
        }
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path file(String key) {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema").resolve(key + ".json");
    }

    public static List<NpcData> load(String key, HolderLookup.Provider registries) {
        List<NpcData> out = new ArrayList<>();
        Path path = file(key);
        if (!Files.exists(path)) return out;
        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            for (JsonElement e : root.getAsJsonArray("npcs")) {
                // 1体壊れていても他は読み込む
                NpcData.CODEC.parse(ops, e)
                        .resultOrPartial(err -> Cinema.LOGGER.warn("NPC load error: {}", err))
                        .ifPresent(out::add);
            }
        } catch (Exception ex) {
            Cinema.LOGGER.error("Failed to load {}", path, ex);
        }
        return out;
    }

    public static void save(String key, HolderLookup.Provider registries, List<NpcData> defs) {
        Path path = file(key);
        try {
            Files.createDirectories(path.getParent());
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            JsonArray arr = new JsonArray();
            for (NpcData d : defs) {
                NpcData.CODEC.encodeStart(ops, d)
                        .resultOrPartial(err -> Cinema.LOGGER.warn("NPC save error: {}", err))
                        .ifPresent(arr::add);
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", SCHEMA_VERSION);
            root.add("npcs", arr);
            // 書き込み途中のクラッシュ対策: tmp -> move
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Cinema.LOGGER.error("Failed to save {}", path, ex);
        }
    }
}
