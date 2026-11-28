package net.kztmc.mc.cinema.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;

/**
 * NPCのグループ(一覧で、まとめて開閉できる入れ物)。
 * NpcData の保存形式は変えずに、別のファイル(config/cinema/<ワールド名>.groups.json)へ保存する。
 * 一覧の並びは、グループ(作った順)→ グループに入っていないNPC。
 */
public final class NpcGroups {
    private NpcGroups() {}

    private static UUID selectedGroup;

    /** グループのモード(動作は、あとで実装する) */
    public enum Mode { NORMAL, RIDE, FILL }

    public static final class Group {
        private final UUID id;
        private String name;
        private boolean open = true;
        private Mode mode = Mode.NORMAL;

        private Group(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID id() { return id; }

        public String name() { return name; }

        public boolean isOpen() { return open; }

        public Mode mode() { return mode; }

        /** 一覧・パネルに出す名前(空なら「グループ」) */
        public String displayName() { return name.isBlank() ? "グループ" : name; }
    }

    private static class GroupData {
        String id;
        String name;
        boolean open = true;
        String mode;
    }

    private static class FileData {
        List<GroupData> groups = new ArrayList<>();
        Map<String, String> members = new HashMap<>();   // NPCのID -> グループのID
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Group> GROUPS = new ArrayList<>();
    private static final Map<UUID, UUID> MEMBER_OF = new HashMap<>();

    // ---- 読み取り ----

    public static List<Group> groups() {
        return List.copyOf(GROUPS);
    }

    public static Group find(UUID id) {
        for (Group g : GROUPS) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }

    /** NPCが入っているグループ。入っていなければ null */
    public static Group groupOf(UUID npc) {
        UUID group = MEMBER_OF.get(npc);
        return group == null ? null : find(group);
    }

    public static String nextDefaultName() {
        return "グループ " + (GROUPS.size() + 1);
    }

    // ---- 変更(どれも、保存の対象にする) ----

    public static Group add(String name) {
        Group g = new Group(UUID.randomUUID(), name);
        GROUPS.add(g);
        NpcManager.markDirty();
        return g;
    }

    public static void rename(UUID id, String name) {
        Group g = find(id);
        if (g == null) return;
        g.name = name == null ? "" : name;
        NpcManager.markDirty();
    }

    public static void setOpen(UUID id, boolean open) {
        Group g = find(id);
        if (g == null) return;
        g.open = open;
        NpcManager.markDirty();
    }

    /** グループを消す。入っていたNPCは、消さずに、グループの外へ出る */
    public static void remove(UUID id) {
        GROUPS.removeIf(g -> g.id.equals(id));
        MEMBER_OF.values().removeIf(id::equals);
        if (id.equals(selectedGroup)) selectedGroup = null;
        NpcManager.markDirty();
    }

    /** NPCを、グループへ入れる。group が null(または存在しない)なら、グループの外へ出す */
    public static void assign(UUID npc, UUID group) {
        if (group == null || find(group) == null) MEMBER_OF.remove(npc);
        else MEMBER_OF.put(npc, group);
        NpcManager.markDirty();
    }

    /** NPCを消したとき */
    public static void forgetNpc(UUID npc) {
        if (MEMBER_OF.remove(npc) != null) NpcManager.markDirty();
    }

    /** 複製: 同じグループへ入れる */
    public static void copyMembership(UUID from, UUID to) {
        UUID group = MEMBER_OF.get(from);
        if (group != null) MEMBER_OF.put(to, group);
    }

    /** 切断時 */
    public static void clear() {
        GROUPS.clear();
        MEMBER_OF.clear();
        selectedGroup = null;
    }

    // ---- 保存と読み込み ----

    private static Path file(String worldKey) {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema").resolve(worldKey + ".groups.json");
    }

    public static void load(String worldKey) {
        clear();
        Path path = file(worldKey);
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            FileData data = GSON.fromJson(reader, FileData.class);
            if (data == null) return;
            for (GroupData gd : data.groups) {
                try {
                    Group g = new Group(UUID.fromString(gd.id), gd.name == null ? "" : gd.name);
                    g.open = gd.open;
                    try {
                        if (gd.mode != null) g.mode = Mode.valueOf(gd.mode);
                    } catch (IllegalArgumentException ignored) {
                        // 知らないモードは、NORMAL のまま
                    }
                    GROUPS.add(g);
                } catch (IllegalArgumentException ignored) {
                    // 壊れたIDは、飛ばす
                }
            }
            data.members.forEach((npc, group) -> {
                try {
                    MEMBER_OF.put(UUID.fromString(npc), UUID.fromString(group));
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
        if (GROUPS.isEmpty() && !Files.exists(path)) return;   // グループを使っていないワールドには、ファイルを作らない
        try {
            Files.createDirectories(path.getParent());
            FileData data = new FileData();
            for (Group g : GROUPS) {
                GroupData gd = new GroupData();
                gd.id = g.id.toString();
                gd.name = g.name;
                gd.open = g.open;
                gd.mode = g.mode.name();
                data.groups.add(gd);
            }
            MEMBER_OF.forEach((npc, group) -> data.members.put(npc.toString(), group.toString()));
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException | RuntimeException ex) {
            Cinema.LOGGER.warn("[Cinema] could not write {}", path, ex);
        }
    }


    /** 選択中のグループ。なければ null */
    public static Group selected() {
        Group g = selectedGroup == null ? null : find(selectedGroup);
        if (g == null) selectedGroup = null;
        return g;
    }

    public static UUID selectedId() {
        Group g = selected();
        return g == null ? null : g.id;
    }

    /** グループを選ぶ(null で外す)。NPCの選択は、外れる */
    public static void select(UUID id) {
        selectedGroup = id;
        if (id != null) NpcManager.select(null);
    }

    public static void setMode(UUID id, Mode mode) {
        Group g = find(id);
        if (g == null) return;
        g.mode = mode;
        NpcManager.markDirty();
    }
}