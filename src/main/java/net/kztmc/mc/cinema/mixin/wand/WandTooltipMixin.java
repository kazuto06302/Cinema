package net.kztmc.mc.cinema.mixin.wand;

import net.kztmc.mc.cinema.CinemaWand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public abstract class WandTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void cinema$onlyName(Item.TooltipContext context, Player player, TooltipFlag flag,
                                 CallbackInfoReturnable<List<Component>> cir) {
        if (!CinemaWand.isWand((ItemStack) (Object) this)) return;
        List<Component> lines = cir.getReturnValue();
        if (lines.size() > 1) cir.setReturnValue(new ArrayList<>(lines.subList(0, 1)));
    }
}