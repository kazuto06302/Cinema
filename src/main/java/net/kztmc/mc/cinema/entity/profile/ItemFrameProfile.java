package net.kztmc.mc.cinema.entity.profile;

import java.util.List;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.ui.panel.right.EquipmentEdit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 額縁・光る額縁。メインハンドに入れたアイテムが、中身になる。中身の回転は、8段階。
 * 上向き・下向き(頭の上下)にもできる。
 */
public final class ItemFrameProfile extends HangingProfile {
    private static final String ROTATION = "rotation";

    public ItemFrameProfile() {
        super("minecraft:item_frame", "minecraft:glow_item_frame");
        heldItem("中身", stack -> true);
        dropdownChoice(ROTATION, "中身の回転", 0, "0°", "45°", "90°", "135°", "180°", "225°", "270°", "315°");
    }

    @Override
    protected double[] sizeOf(Entity e) {
        return new double[] {0.75, 0.75};   // 額縁の大きさ(12ドット)
    }

    @Override
    protected boolean allowsVertical() {
        return true;
    }

    @Override
    public void applyData(Entity e, NpcData d) {
        // 1. 中身と回転を、先に入れる(これらが、位置と当たり判定を、吸着し直すので)。変わっていないときは、入れ直さない
        ItemStack main = EquipmentEdit.stackOf(d, EquipmentSlot.MAINHAND);
        ItemStack wanted = main.isEmpty() ? ItemStack.EMPTY : main.copyWithCount(1);
        if (!(EntityReflect.call(e, "getItem") instanceof ItemStack current) || !ItemStack.matches(current, wanted)) {
            EntityReflect.invokeFirst(e, List.of("setItem"), wanted, false);   // false: 音や更新の通知を出さない
        }

        int rotation = d.option(ROTATION, 0);
        if (!(EntityReflect.call(e, "getRotation") instanceof Number now) || now.intValue() != rotation) {
            if (!EntityReflect.invokeQuiet(e, List.of("setRotation"), rotation, false)) {
                EntityReflect.invokeFirst(e, List.of("setRotation"), rotation);
            }
        }

        // 2. 位置は、最後に、吸着させずに置く
        placeFreely(e, d);
    }
}