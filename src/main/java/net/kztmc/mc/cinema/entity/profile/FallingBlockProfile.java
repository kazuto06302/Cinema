package net.kztmc.mc.cinema.entity.profile;

import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.ui.panel.right.EquipmentEdit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 落下ブロック。メインハンドに入れたブロックのアイテムが、そのブロックの既定の BlockState になる(何もなければ砂)。 */
public final class FallingBlockProfile extends EntityProfile {
    public FallingBlockProfile() {
        super("minecraft:falling_block");
        noWalk();
        heldItem("ブロック", stack -> stack.getItem() instanceof BlockItem);
    }

    @Override
    public void applyData(Entity e, NpcData d) {
        ItemStack main = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);
        BlockState state = main.getItem() instanceof BlockItem item
                ? item.getBlock().defaultBlockState()
                : Blocks.SAND.defaultBlockState();
        EntityReflect.setObject(e, "blockState", state);
    }
}