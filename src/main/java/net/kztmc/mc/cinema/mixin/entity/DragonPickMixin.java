package net.kztmc.mc.cinema.mixin.entity;

import net.kztmc.mc.cinema.npc.SceneFreeze;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NPCのエンダードラゴン(本体とパーツ)を、クロスヘアの対象にしない。 */
@Mixin({EnderDragon.class, EnderDragonPart.class})
public abstract class DragonPickMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true, require = 0)
    private void cinema$notPickable(CallbackInfoReturnable<Boolean> cir) {
        if (SceneFreeze.isFrozenNpc((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}