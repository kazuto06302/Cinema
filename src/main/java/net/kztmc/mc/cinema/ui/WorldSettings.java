package net.kztmc.mc.cinema.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/** ワールド設定パネルの、保存する設定。時間・天候は、ゲームルールが持つので、ここには保存しない */
public final class WorldSettings {
    private WorldSettings() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cinema/world_panel.json");

    private static class Data {
        boolean textureFixed;
        int textureTick;
    }

    public static boolean textureFixed;
    public static int textureTick;

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d == null) return;
            textureFixed = d.textureFixed;
            textureTick = Math.max(0, Math.min(20, d.textureTick));
        } catch (IOException | RuntimeException e) {
            Cinema.LOGGER.warn("[Cinema] could not read world_panel.json", e);
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.textureFixed = textureFixed;
            d.textureTick = textureTick;
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Cinema.LOGGER.warn("[Cinema] could not write world_panel.json", e);
        }
    }

    /** ワールドに入ったとき: 保存した固定を、かけ直す */
    public static void applyOnJoin() {
        if (textureFixed) TextureAnimations.fix(textureTick);
    }
}