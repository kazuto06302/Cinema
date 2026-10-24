package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.npc.EntityTweaks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 当たり判定を無効にしたNPC(ボート・イカダなど)とは、ぶつからない。 */
@Mixin(Entity.class)
public abstract class EntityCollisionMixin {
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true, require = 0)
    private void cinema$ignoreNpc(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (EntityTweaks.hasNoHitbox(other)) {
            cir.setReturnValue(false);
        }
    }
}