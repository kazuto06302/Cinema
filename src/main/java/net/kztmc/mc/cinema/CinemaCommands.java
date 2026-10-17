package net.kztmc.mc.cinema;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.npc.SceneFreeze;
import net.kztmc.mc.cinema.ui.CinemaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

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
                        .then(ClientCommands.literal("freeze")
                                .executes(ctx -> {
                                    SceneFreeze.toggle();
                                    say("時間停止 " + (SceneFreeze.isFrozen() ? "ON" : "OFF"));
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    NpcManager.clear();
                                    say("NPCをすべて削除しました");
                                    return 1;
                                }))));

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
    }
}
