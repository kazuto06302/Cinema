package net.kztmc.mc.cinema.ui.panel.left;

import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.ui.npc.MobIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** NPCリストの行に出す、アイコン(プレイヤー / モブ)。 */
public final class NpcFaces {
    private NpcFaces() {}

    public static void paint(GuiGraphicsExtractor g, NpcData d, int x, int y, int size) {
        if (NpcData.PLAYER.equals(d.kind())) {
            MobIcons.paintPlayer(g, Minecraft.getInstance().font, d.skin().orElse(""), x, y, size);
        } else {
            MobIcons.paint(g, Minecraft.getInstance().font, d.entityType(), "", x, y, size);
        }
    }
}