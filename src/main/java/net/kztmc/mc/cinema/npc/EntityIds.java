package net.kztmc.mc.cinema.npc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** エンティティのID割り当て。当たり判定の部位を持つエンティティ(エンダードラゴン)は、部位にも連番を振る。 */
public final class EntityIds {
    private EntityIds() {}

    /** 当たり判定の部位。持たないエンティティは空 */
    public static Entity[] subEntities(Entity e) {
        if (e instanceof EnderDragon dragon) {
            Entity[] parts = dragon.getSubEntities();
            return parts != null ? parts : new Entity[0];
        }
        return new Entity[0];
    }

    /** firstId から連番で割り当てて、次に空いているIDを返す */
    public static int assign(Entity e, int firstId) {
        e.setId(firstId);
        int next = firstId + 1;
        for (Entity part : subEntities(e)) {
            part.setId(next++);
        }
        return next;
    }
}