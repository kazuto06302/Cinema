package net.kztmc.mc.cinema.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/** ワールド設定パネルの、保存する設定(テクスチャ固定と、時間・天候のクライアント上書き) */
public final class WorldSettings {
    private WorldSettings() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cinema/world_panel.json");
    private static final long FLUSH_INTERVAL_MS = 500;

    private static class Data {
        boolean textureFixed;
        int textureTick;
        boolean timeOn;
        int timeTicks;
        boolean weatherOn;
        int weather;
        Map<String, Boolean> render = new HashMap<>();
    }

    public static boolean textureFixed;
    public static int textureTick;

    private static boolean dirty;
    private static long lastWrite;

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d == null) return;
            textureFixed = d.textureFixed;
            textureTick = Math.max(0, Math.min(20, d.textureTick));
            ClientOverrides.timeOn = d.timeOn;
            ClientOverrides.timeTicks = Math.floorMod(d.timeTicks, 24000);
            ClientOverrides.weatherOn = d.weatherOn;
            ClientOverrides.weather = Math.max(0, Math.min(2, d.weather));
            if (d.render != null) for (RenderToggles.Item i : RenderToggles.Item.values()) {
                Boolean v = d.render.get(i.name());
                if (v != null) i.set(v);
            }
        } catch (IOException | RuntimeException e) {
            Cinema.LOGGER.warn("[Cinema] could not read world_panel.json", e);
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.textureFixed = textureFixed;
            d.textureTick = textureTick;
            d.timeOn = ClientOverrides.timeOn;
            d.timeTicks = ClientOverrides.timeTicks;
            d.weatherOn = ClientOverrides.weatherOn;
            d.weather = ClientOverrides.weather;
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(d));
            dirty = false;
            lastWrite = System.currentTimeMillis();
            for (RenderToggles.Item i : RenderToggles.Item.values()) d.render.put(i.name(), i.on());
        } catch (IOException e) {
            Cinema.LOGGER.warn("[Cinema] could not write world_panel.json", e);
        }
    }

    /** スライダーなど、頻繁に変わる値用。あとで flush() がまとめて書く */
    public static void markDirty() {
        dirty = true;
    }

    /** 毎フレーム呼んでよい。印があり、前回の書き込みから間があいていれば、書く */
    public static void flush() {
        if (dirty && System.currentTimeMillis() - lastWrite >= FLUSH_INTERVAL_MS) save();
    }

    /** ワールドに入ったとき: 保存した固定を、かけ直す(時間・天候は、Mixin が毎フレーム見るので、何もしなくてよい) */
    public static void applyOnJoin() {
        if (textureFixed) TextureAnimations.fix(textureTick);
    }
}