package net.kztmc.mc.cinema.mixin.wand;

import net.kztmc.mc.cinema.CinemaWand;
import net.kztmc.mc.cinema.ui.panel.inventory.CinemaCreativeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class WandMenuGuardMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Shadow
    public abstract ItemStack getCarried();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void cinema$guard(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        Object self = this;
        if (self instanceof ChestMenu || self instanceof ShulkerBoxMenu
                || self instanceof HopperMenu || self instanceof DispenserMenu) return;   // 保管系は、許可

        Slot slot = slotId >= 0 && slotId < slots.size() ? slots.get(slotId) : null;
        if (slot == null) return;
        boolean toMachine = !(slot.container instanceof Inventory);   // 作業台の材料欄など

        // 1. ワンドを持って、機械側のスロットを押す(置く・ドラッグする)
        if (toMachine && CinemaWand.isWand(getCarried())) {
            ci.cancel();
            return;
        }

        // 2. ワンドのシフトクリック。
        // 通常のプレイヤーインベントリと、バニラのクリエイティブ画面はMinecraft本来の処理に任せる。
        // CinemaCreativeScreen側ではQUICK_MOVEを画面側で先に拒否する。
        if (input == ContainerInput.QUICK_MOVE && CinemaWand.isWand(slot.getItem())) {
            Screen screen = Minecraft.getInstance().gui.screen();
            boolean vanillaInventory = self instanceof InventoryMenu;
            boolean vanillaCreativeInventory =
                    screen instanceof CreativeModeInventoryScreen
                            && !(screen instanceof CinemaCreativeScreen);

            if (!vanillaInventory && !vanillaCreativeInventory) {
                ci.cancel();
                return;
            }
        }

        // 3. 数字キー・オフハンドキーで、ワンドを機械側のスロットへ入れ替える
        if (input == ContainerInput.SWAP && toMachine) {
            ItemStack other = button >= 0 && button < 9
                    ? player.getInventory().getItem(button)
                    : player.getOffhandItem();

            if (CinemaWand.isWand(other)) ci.cancel();
        }
    }
}
