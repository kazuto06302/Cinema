package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.npc.SceneFreeze;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 止めている間、NPCだけを「ティックしないエンティティ」として扱う。 */
@Mixin(TickRateManager.class)
public abstract class TickRateManagerMixin {
    @Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
    private void cinema$freezeNpc(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SceneFreeze.isFrozenNpc(entity)) {
            cir.setReturnValue(true);
        }
    }
}