package net.kztmc.mc.cinema.npc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/** プレイヤー追加の履歴。config/cinema/players.txt に1行1名で保存する。新しい順。 */
public final class PlayerHistory {
    private PlayerHistory() {}

    private static final int MAX = 60;
    private static final List<String> NAMES = new ArrayList<>();
    private static boolean loaded;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema").resolve("players.txt");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(file())) {
                for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                    String name = line.trim();
                    if (!name.isEmpty()) NAMES.add(name);
                }
            }
        } catch (IOException e) {
            Cinema.LOGGER.warn("Failed to load player history", e);
        }
    }

    public static List<String> names() {
        load();
        return List.copyOf(NAMES);
    }

    /** 先頭に追加する。同じ名前(大文字小文字は区別しない)があれば移動 */
    public static void add(String name) {
        load();
        NAMES.removeIf(n -> n.equalsIgnoreCase(name));
        NAMES.add(0, name);
        while (NAMES.size() > MAX) NAMES.remove(NAMES.size() - 1);
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), NAMES, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Cinema.LOGGER.warn("Failed to save player history", e);
        }
    }
}