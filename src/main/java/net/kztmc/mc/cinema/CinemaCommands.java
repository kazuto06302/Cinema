package net.kztmc.mc.cinema;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 動作確認用の暫定コマンド(GUI実装までのつなぎ)。
 * Fabric API 26.x で ClientCommands / ClientCommandRegistrationCallback の名前が
 * 変わっていたら、この1ファイルだけ直せばよい。
 *   /cinema player [name]
 *   /cinema mob <entity_id>
 *   /cinema clear
 */
public final class CinemaCommands {
    private CinemaCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("cinema")
                        .then(ClientCommands.literal("player")
                                .executes(ctx -> spawn(NpcData.PLAYER, "minecraft:player", "NPC"))
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> spawn(NpcData.PLAYER, "minecraft:player",
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(ClientCommands.literal("mob")
                                .then(ClientCommands.argument("entity", StringArgumentType.word())
                                        .executes(ctx -> spawn(NpcData.MOB,
                                                StringArgumentType.getString(ctx, "entity"), null))))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    NpcManager.clear();
                                    say("NPCをすべて削除しました");
                                    return 1;
                                }))));
    }

    private static int spawn(String kind, String entityType, String skin) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || Minecraft.getInstance().level == null) return 0;

        String id = entityType.contains(":") ? entityType : "minecraft:" + entityType;
        float faceYaw = p.getYRot() + 180f; // プレイヤーの方を向かせる
        NpcData data = new NpcData(
                UUID.randomUUID(), kind, id, Optional.ofNullable(skin),
                Minecraft.getInstance().level.dimension().identifier().toString(),
                p.getX(), p.getY(), p.getZ(),
                faceYaw, 0f, faceYaw, faceYaw,
                Map.of());
        NpcManager.add(data);
        say("NPCを追加しました: " + id);
        return 1;
    }

    private static void say(String msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.sendOverlayMessage(Component.literal("[Cinema] " + msg));
    }
}
