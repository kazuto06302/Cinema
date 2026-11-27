package net.kztmc.mc.cinema.ui.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/** 画面の設定(テーマカラー・不透明度)を、config/cinema/ui_settings.json に保存する */
public final class UiSettings {
    private UiSettings() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cinema/ui_settings.json");

    private static class Data {
        int themeColor = Theme.DEFAULT_THEME;
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d == null) return;
            Theme.setThemeColor(d.themeColor);
        } catch (IOException | RuntimeException e) {
            Cinema.LOGGER.warn("[Cinema] could not read ui_settings.json", e);
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.themeColor = Theme.themeColor();
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(d));
        } catch (IOException e) {
            Cinema.LOGGER.warn("[Cinema] could not write ui_settings.json", e);
        }
    }
}