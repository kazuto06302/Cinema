package net.kztmc.mc.cinema.mixin;

import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.npc.SceneFreeze;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NPCへの攻撃を、サーバーに送らない(NPCはクライアントだけの偽のエンティティで、IDもサーバーと無関係)。
 * IDが割り当てられていないエンティティを殴ってクラッシュするのも防ぎ、そのクラス名をログに出す。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void cinema$guardAttack(Player player, Entity target, CallbackInfo ci) {
        try {
            target.getId();   // IDが未割り当てだと、ここで例外になる
        } catch (IllegalStateException ex) {
            Cinema.LOGGER.warn("[Cinema] attack on an entity without ID: {}", target.getClass().getName());
            ci.cancel();
            return;
        }
        if (SceneFreeze.isFrozenNpc(target)) {
            ci.cancel();
        }
    }
}