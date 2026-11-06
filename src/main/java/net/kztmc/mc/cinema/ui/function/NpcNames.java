package net.kztmc.mc.cinema.ui.function;

import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.ui.npc.MobCatalog;

/** NPCの表示名(プレイヤーはスキン名、モブは翻訳された名前)。 */
public final class NpcNames {
    private NpcNames() {}

    public static String of(NpcData d) {
        return NpcData.PLAYER.equals(d.kind()) ? d.skin().orElse("NPC") : MobCatalog.nameOf(d.entityType());
    }
}