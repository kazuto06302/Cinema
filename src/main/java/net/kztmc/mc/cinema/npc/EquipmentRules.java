package net.kztmc.mc.cinema.npc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** どのスロットに、どのアイテムを入れられるか。 */
public final class EquipmentRules {
    private EquipmentRules() {}

    /**
     * @param slot       右パネルのスロット(メイン・オフ・頭・胴・脚・足)
     * @param animalGear 動物の装備があるモブか。その場合、胴は動物の胴(防具・カーペット)、脚は鞍として判定する
     */
    public static boolean accepts(EquipmentSlot slot, ItemStack stack, boolean animalGear) {
        if (stack.isEmpty()) return true;
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) return true;   // 手は、何でも持てる

        EquipmentSlot need = slot;
        if (animalGear) {
            if (slot == EquipmentSlot.CHEST) need = EquipmentSlot.BODY;
            else if (slot == EquipmentSlot.LEGS) need = EquipmentSlot.SADDLE;
        }
        var equippable = stack.get(DataComponents.EQUIPPABLE);   // 着られるアイテムは、どのスロットかを持っている
        return equippable != null && equippable.slot() == need;
    }
}