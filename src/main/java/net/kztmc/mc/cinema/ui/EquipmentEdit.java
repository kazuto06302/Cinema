package net.kztmc.mc.cinema.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 装備の読み書きと、表示の共通処理(Cinema画面と、標準インベントリの上の装備パネルで共有する)。 */
public final class EquipmentEdit {
    private EquipmentEdit() {}

    /** 右パネルで扱う装備スロット */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static ItemStack stackOf(NpcData d, EquipmentSlot slot) {
        ItemStack s = d.equipment().get(slot.getName());
        return s == null ? ItemStack.EMPTY : s;
    }

    public static void set(UUID id, EquipmentSlot slot, ItemStack stack) {
        NpcManager.get(id).ifPresent(cur -> {
            Map<String, ItemStack> map = new HashMap<>(cur.equipment());
            if (stack.isEmpty()) map.remove(slot.getName());
            else map.put(slot.getName(), stack);
            NpcManager.replace(cur.withEquipment(map));
        });
    }

    /** 空のスロットに、薄く出す見本のアイテム */
    public static ItemStack ghostOf(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> new ItemStack(Items.IRON_SWORD);
            case OFFHAND -> new ItemStack(Items.SHIELD);
            case HEAD -> new ItemStack(Items.IRON_HELMET);
            case CHEST -> new ItemStack(Items.IRON_CHESTPLATE);
            case LEGS -> new ItemStack(Items.IRON_LEGGINGS);
            case FEET -> new ItemStack(Items.IRON_BOOTS);
            default -> ItemStack.EMPTY;
        };
    }

    /** 動物の装備があるモブでは、胴は動物の胴(防具・カーペット)、脚は鞍として扱う */
    public static String label(EquipmentSlot slot, boolean animalGear) {
        return switch (slot) {
            case MAINHAND -> "メイン";
            case OFFHAND -> "オフ";
            case HEAD -> "頭";
            case CHEST -> animalGear ? "胴(防具)" : "胴";
            case LEGS -> animalGear ? "脚(鞍)" : "脚";
            case FEET -> "足";
            default -> slot.getName();
        };
    }
}