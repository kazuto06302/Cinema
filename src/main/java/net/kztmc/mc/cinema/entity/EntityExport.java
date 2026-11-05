package net.kztmc.mc.cinema.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.npc.NpcPose;
import net.kztmc.mc.cinema.ui.MobCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** /cinema entities: NPC にできるエンティティの一覧と、プロフィールの内容を、TSV に書き出す */
public final class EntityExport {
    private EntityExport() {}

    public static void run() {
        Minecraft mc = Minecraft.getInstance();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cinema/entities.tsv");

        // NPC モブ一覧(devモードの分も含む)にあるもの: id → 表示名
        Map<String, String> catalog = new HashMap<>();
        for (MobCatalog.Entry e : MobCatalog.filter("", true)) catalog.put(e.id(), e.name());

        StringBuilder sb = new StringBuilder();
        sb.append(String.join("\t", "id", "一覧に載る", "表示名", "カテゴリ", "summon可",
                "プロフィール", "歩く", "人型", "サイズなし", "動物装備", "持ち物ラベル", "姿勢", "オプション")).append('\n');

        int count = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            EntityProfile p = EntityProfiles.of(id);

            StringJoiner poses = new StringJoiner(",");
            for (NpcPose pose : p.poses()) poses.add(pose.label);

            StringJoiner options = new StringJoiner(" | ");
            for (EntityOption o : p.options()) options.add(o.label() + "(" + o.kind() + ")");

            sb.append(String.join("\t",
                    id,
                    catalog.containsKey(id) ? "○" : "",
                    catalog.getOrDefault(id, ""),
                    String.valueOf(type.getCategory()),
                    type.canSummon() ? "○" : "",
                    p.getClass().getSimpleName(),
                    p.walks() ? "○" : "",
                    p.isHumanoid() ? "○" : "",
                    p.hasNoScale() ? "○" : "",
                    p.hasAnimalGear() ? "○" : "",
                    p.heldItemLabel() == null ? "" : p.heldItemLabel(),
                    poses.toString(),
                    options.toString())).append('\n');
            count++;
        }

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString());
            Cinema.LOGGER.info("[Cinema] wrote {} entities to {}", count, file);
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("[Cinema] " + count + "件を config/cinema/entities.tsv に書き出しました"));
            }
        } catch (IOException ex) {
            Cinema.LOGGER.warn("[Cinema] could not write entities.tsv", ex);
        }
    }
}