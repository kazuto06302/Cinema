package net.kztmc.mc.cinema.npc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/** ケープの一覧表(capes.csv)。config/cinema/capes.csv があれば、それを優先する */
public final class CapeCatalog {
    private CapeCatalog() {}

    public record Entry(String id, String name, String hash) {}

    private static final String RESOURCE = "/data/cinema/capes.csv";
    private static List<Entry> all;

    public static List<Entry> all() {
        if (all == null) all = read();
        return all;
    }

    public static void reload() {
        all = null;
    }

    public static Optional<Entry> find(String id) {
        return all().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private static List<Entry> read() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cinema/capes.csv");
        try (InputStream in = Files.exists(file) ? Files.newInputStream(file) : CapeCatalog.class.getResourceAsStream(RESOURCE)) {
            List<Entry> out = new ArrayList<>();
            if (in == null) return out;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean header = true;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.isBlank() || line.startsWith("#")) continue;
                if (header) {   // 見出しの行
                    header = false;
                    continue;
                }
                String[] c = line.split(",", -1);
                if (c.length < 3) continue;

                String id = parseField(c[0]);
                String name = parseField(c[1]);
                String hash = parseField(c[2]);

                if (id.isBlank()) continue;

                out.add(new Entry(id, name, hash));
            }
            return out;
        } catch (IOException ex) {
            Cinema.LOGGER.warn("[Cinema] could not read capes.csv", ex);
            return new ArrayList<>();
        }
    }

    private static String parseField(String value) {
        String result = value.trim();

        // CSVの外側のダブルクォーテーションを除去
        if (result.length() >= 2
                && result.startsWith("\"")
                && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }

        // CSV内のエスケープされたダブルクォーテーションを復元
        return result.replace("\"\"", "\"");
    }
}