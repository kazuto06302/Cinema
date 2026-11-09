package net.kztmc.mc.cinema.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.npc.NpcPose;
import net.kztmc.mc.cinema.ui.npc.MobCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** /cinema entities: 現在の設定を、読み込める形(entities.csv)で書き出す。「name」以降の列は、読み込み時には無視される */
public final class EntityExport {
    private EntityExport() {}

    private static final String[] HEADER = {"id", "listed", "icon", "walks", "humanoid", "scalable", "hitbox",
            "equipment", "held_label", "poses", "name", "category", "summon", "behavior", "options"};

    public static void run() {
        Path file = EntityTable.configFile();

        Map<String, String> names = new HashMap<>();
        for (MobCatalog.Entry e : MobCatalog.filter("", true)) names.put(e.id(), e.name());

        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        ids.sort(String::compareTo);

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("# 〇=はい ×=いいえ 空欄=コードの設定のまま / equipment: full, animal, main(メインハンドだけ), none / poses: STANDING/CROUCHING/SWIMMING/FALL_FLYING/SITTING\n");
        sb.append(String.join(",", HEADER)).append('\n');

        for (String id : ids) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(id));
            EntityProfile p = EntityProfiles.of(id);

            StringJoiner poses = new StringJoiner("/");
            for (NpcPose pose : p.poses()) poses.add(pose.name());
            StringJoiner options = new StringJoiner(" | ");
            for (EntityOption o : p.options()) options.add(o.label() + "(" + o.kind() + ")");

            String[] cells = {
                    id,
                    yn(!p.isHidden()),
                    yn(!p.isNoRender()),
                    yn(p.walks()),
                    yn(p.isHumanoid()),
                    yn(!p.hasNoScale()),
                    yn(!p.hasNoHitbox()),
                    p.equipmentMode().name().toLowerCase(java.util.Locale.ROOT),
                    p.heldItemLabel() == null ? "" : p.heldItemLabel(),
                    poses.toString(),
                    names.getOrDefault(id, ""),
                    String.valueOf(type.getCategory()),
                    yn(type.canSummon()),
                    p.getClass().getSimpleName(),
                    options.toString()};
            StringJoiner row = new StringJoiner(",");
            for (String c : cells) row.add(quote(c));
            sb.append(row).append('\n');
        }

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString());
            Cinema.LOGGER.info("[Cinema] wrote {} entities to {}", ids.size(), file);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("[Cinema] " + ids.size() + "件を " + file.getFileName() + " に書き出しました"));
            }
        } catch (IOException ex) {
            Cinema.LOGGER.warn("[Cinema] could not write entities.csv", ex);
        }
    }

    private static String yn(boolean v) {
        return v ? "y" : "n";
    }

    private static String quote(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}