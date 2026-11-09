package net.kztmc.mc.cinema.entity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.npc.NpcPose;

/** entities.csv(エンティティごとのフラグの表)を読んで、プロフィールに反映する */
public final class EntityTable {
    private EntityTable() {}

    private static final String RESOURCE = "/data/cinema/entities.csv";

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema/entities.csv");
    }

    /** EntityProfiles の初期化から、1回だけ呼ばれる */
    static void applyAll() {
        List<Map<String, String>> rows = read();
        if (rows.isEmpty()) {
            Cinema.LOGGER.warn("[Cinema] entities.csv was not found or is empty. Run /cinema entities to create it");
            return;
        }
        for (Map<String, String> r : rows) {
            String id = r.getOrDefault("id", "").trim();
            if (id.isEmpty()) continue;
            EntityProfile p = EntityProfiles.ensure(id);

            Boolean v;
            if ((v = flag(r, "listed")) != null) p.listed(v);
            if ((v = flag(r, "icon")) != null) p.rendersIcon(v);
            if ((v = flag(r, "walks")) != null) p.walking(v);
            if ((v = flag(r, "humanoid")) != null) p.humanoid(v);     // 人型は、姿勢の既定も決めるので、姿勢より先に
            if ((v = flag(r, "scalable")) != null) p.scalable(v);
            if ((v = flag(r, "hitbox")) != null) p.hitbox(v);

            String eq = r.getOrDefault("equipment", "").trim();
            if (!eq.isEmpty()) {
                try {
                    p.equipment(EquipmentMode.valueOf(eq.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    Cinema.LOGGER.warn("[Cinema] entities.csv: unknown equipment '{}' for {}", eq, id);
                }
            }
            p.heldLabel(r.get("held_label"));

            String poses = r.getOrDefault("poses", "").trim();
            if (!poses.isEmpty()) {
                List<NpcPose> list = new ArrayList<>();
                for (String name : poses.split("/")) {
                    try {
                        list.add(NpcPose.valueOf(name.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        Cinema.LOGGER.warn("[Cinema] entities.csv: unknown pose '{}' for {}", name, id);
                    }
                }
                if (!list.isEmpty()) p.poses(list.toArray(new NpcPose[0]));
            }
        }
        Cinema.LOGGER.info("[Cinema] entities.csv: {} rows applied", rows.size());
    }

    private static Boolean flag(Map<String, String> row, String key) {
        String s = row.getOrDefault(key, "").trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        return switch (s) {
            case "y", "yes", "○", "true", "1" -> Boolean.TRUE;
            case "n", "no", "×", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    // ---- CSV の読み込み ----

    private static List<Map<String, String>> read() {
        try {
            Path file = configFile();
            if (Files.exists(file)) {
                try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return parse(in);
                }
            }
            try (InputStream stream = EntityTable.class.getResourceAsStream(RESOURCE)) {
                if (stream == null) return List.of();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    return parse(in);
                }
            }
        } catch (IOException | RuntimeException ex) {
            Cinema.LOGGER.warn("[Cinema] could not read entities.csv", ex);
            return List.of();
        }
    }

    private static List<Map<String, String>> parse(BufferedReader in) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> header = null;
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("\uFEFF")) line = line.substring(1);   // Excel の BOM
            if (line.isBlank() || line.startsWith("#")) continue;
            List<String> cells = splitCsv(line);
            if (header == null) {
                header = new ArrayList<>();
                for (String h : cells) header.add(h.trim().toLowerCase(Locale.ROOT));
                continue;
            }
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size() && i < cells.size(); i++) row.put(header.get(i), cells.get(i));
            rows.add(row);
        }
        return rows;
    }

    /** 引用符("...")と、その中の "" に対応した、1行の分割 */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    public static void reload() {
        applyAll();
        for (net.kztmc.mc.cinema.npc.NpcData d : net.kztmc.mc.cinema.npc.NpcManager.defs()) {
            net.kztmc.mc.cinema.npc.NpcManager.replace(d);   // 当たり判定・サイズなどを、新しい設定で作り直す
        }
    }
}