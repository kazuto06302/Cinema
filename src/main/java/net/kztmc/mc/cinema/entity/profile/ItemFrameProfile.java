package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.ui.EquipmentEdit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** 額縁・光る額縁。メインハンドに入れたアイテムが、中身になる。 */
public final class ItemFrameProfile extends HangingProfile {
    public ItemFrameProfile() {
        super("minecraft:item_frame", "minecraft:glow_item_frame");
        heldItem("中身", stack -> true);
    }

    @Override
    protected double[] sizeOf(Entity e) {
        return new double[] {0.75, 0.75};   // 額縁の大きさ(12ドット)
    }

    @Override
    public void applyData(Entity e, NpcData d) {
        super.applyData(e, d);
        ItemStack main = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);
        // 第2引数 false: 音や更新の通知を出さない
        EntityReflect.invokeFirst(e, List.of("setItem"), main.isEmpty() ? ItemStack.EMPTY : main.copy(), false);
    }
}