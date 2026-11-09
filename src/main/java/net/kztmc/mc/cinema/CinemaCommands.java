package net.kztmc.mc.cinema;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.kztmc.mc.cinema.entity.EntityExport;
import net.kztmc.mc.cinema.entity.EntityMotion;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.entity.EntityTable;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.*;
import net.kztmc.mc.cinema.ui.panel.UiSounds;
import net.kztmc.mc.cinema.ui.panel.world.TextureAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 *   /cinema gui              編集画面を開く
 *   /cinema player [name]
 *   /cinema mob <entity_id>
 *   /cinema clear
 */
public final class CinemaCommands {
    private CinemaCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("cinema")
                        .then(ClientCommands.literal("gui")
                                .executes(ctx -> {
                                    openGui();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("player")
                                .executes(ctx -> spawn(NpcData.PLAYER, "minecraft:player", "NPC"))
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> spawn(NpcData.PLAYER, "minecraft:player",
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(ClientCommands.literal("mob")
                                .then(ClientCommands.argument("entity", StringArgumentType.word())
                                        .executes(ctx -> spawn(NpcData.MOB,
                                                StringArgumentType.getString(ctx, "entity"), null))))
                        .then(ClientCommands.literal("fields")
                                .then(ClientCommands.argument("entity", StringArgumentType.word())
                                        .executes(ctx -> {
                                            dumpFields(StringArgumentType.getString(ctx, "entity"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("atlas").executes(ctx -> {
                            TextureAnimations.dump();
                            return 1;
                        }))
                        .then(ClientCommands.literal("entities")
                                .executes(ctx -> {
                                    EntityExport.run();
                                    return 1;
                                })
                                .then(ClientCommands.literal("reload").executes(ctx -> {
                                    EntityTable.reload();
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("[Cinema] entities.csv を読み込み直しました"));
                                    return 1;
                                })))
                        .then(ClientCommands.literal("wand").executes(ctx -> {
                            CinemaWand.give();
                            return 1;
                        }))
                        .then(ClientCommands.literal("use").executes(ctx -> {
                            UUID id = NpcManager.selectedId();
                            Entity e = id == null ? null : NpcManager.entityOf(id);
                            if (e instanceof LivingEntity l) {
                                ItemStack main = l.getItemBySlot(EquipmentSlot.MAINHAND);
                                Cinema.LOGGER.info("[Cinema] use: item={} anim={} duration={} isUsing={} ticksUsing={} remaining={} hand={} spearTag={}",
                                        main.getItem(), main.getUseAnimation().name(), main.getUseDuration(l),
                                        l.isUsingItem(), l.getTicksUsingItem(), l.getUseItemRemainingTicks(),
                                        l.getUsedItemHand(), EntityMotion.isSpear(main));
                            }
                            return 1;
                        }))
                        .then(ClientCommands.literal("togglesig").executes(ctx -> {
                            dumpClass("net.minecraft.client.gui.Hud", "score", "title", "overlay", "action", "subtitle", "extract");
                            dumpClass("net.minecraft.client.renderer.blockentity.BeaconRenderer");
                            dumpClass("net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState");
                            dumpClass("net.minecraft.client.renderer.entity.state.EntityRenderState", "name");
                            dumpClass("net.minecraft.client.renderer.entity.EntityRenderer", "name", "extract");
                            return 1;
                        }))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    NpcManager.clear();
                                    say("NPCをすべて削除しました");
                                    return 1;
                                }))
                ));

    }

    private static void openGui() {
        CinemaScreen.requestOpen();
    }

    private static int spawn(String kind, String entityType, String skin) {
        String id = entityType.contains(":") ? entityType : "minecraft:" + entityType;
        NpcData data = NpcManager.spawnAtPlayer(kind, id, skin);
        if (data == null) return 0;
        say("NPCを追加しました: " + id);
        return 1;
    }

    private static void say(String msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.sendOverlayMessage(Component.literal("[Cinema] " + msg));
        UiSounds.success();
    }

    /** モブの全フィールドとメソッドをログに出す(フィールド名の確認用) */
    private static void dumpFields(String raw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String id = raw.contains(":") ? raw : "minecraft:" + raw;
        Identifier typeId = Identifier.tryParse(id);
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            say("不明なエンティティ: " + id);
            return;
        }
        Entity e = type.create(mc.level, EntitySpawnReason.LOAD);
        if (e == null) {
            say("作成できませんでした: " + id);
            return;
        }
        EntityReflect.dumpEntity(e);
        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        EntityReflect.dumpObject(renderer);
        Object model = EntityReflect.getObject(renderer, "model");
        if (model != null) EntityReflect.dumpObject(model);
        say("ログに出力しました: " + id);
    }

    private static String typeDesc(Class<?> c) {
        if (c == void.class) return "V";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == short.class) return "S";
        if (c == char.class) return "C";
        if (c.isArray()) return "[" + typeDesc(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    /** 診断: クラスのメソッド(ディスクリプタつき)とフィールドを、ログに出す。キーワードがあれば、名前に含むものだけ */
    private static void dumpClass(String className, String... keywords) {
        try {
            Class<?> c = Class.forName(className);
            Cinema.LOGGER.info("[Cinema] ===== {} =====", className);
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic() || !matchesAny(m.getName(), keywords)) continue;
                StringBuilder d = new StringBuilder("(");
                for (Class<?> p : m.getParameterTypes()) d.append(typeDesc(p));
                d.append(")").append(typeDesc(m.getReturnType()));
                Cinema.LOGGER.info("[Cinema] {}{}", m.getName(), d);
            }
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || !matchesAny(f.getName(), keywords)) continue;
                Cinema.LOGGER.info("[Cinema] field {} : {}", f.getName(), typeDesc(f.getType()));
            }
        } catch (ClassNotFoundException e) {
            Cinema.LOGGER.info("[Cinema] class not found: {}", className);
        }
    }

    private static boolean matchesAny(String name, String[] keywords) {
        if (keywords.length == 0) return true;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String k : keywords) {
            if (lower.contains(k)) return true;
        }
        return false;
    }
}
