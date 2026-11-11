package net.kztmc.mc.cinema;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.CinemaScreen;
import net.kztmc.mc.cinema.ui.core.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cinema ワンド。
 *   リーチ内の NPC をクリック → その NPC を選んで、GUI を開く
 *   リーチ外、または何もない所を右クリック → 選択を外して、ワールド設定の GUI を開く
 */
public final class CinemaWand {
    private CinemaWand() {}

    private static final String TAG = "cinema_wand";

    /** NpcManager が、NPC への右クリックを FAIL で消費するので、それより前の段階で受ける */
    private static final Identifier EARLY = Identifier.fromNamespaceAndPath(Cinema.MOD_ID, "wand");

    public static void init() {
        UseItemCallback.EVENT.register((player, level, hand) -> handle(player, level, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> handle(player, level, hand));
        UseEntityCallback.EVENT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(EARLY, (player, level, hand, entity, hit) -> handle(player, level, hand));
    }

    public static boolean isWand(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.BREEZE_ROD)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(TAG);
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.BREEZE_ROD);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("cinema", "wand"));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("cinema.item.wand")
                .withStyle(s -> s.withItalic(false).withColor(0x4C9AFF)));
        return stack;
    }

    /** /cinema wand: クリエイティブで、手に持っているスロットに入れる */
    public static void give() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        if (!player.getAbilities().instabuild) {
            player.sendOverlayMessage(Component.literal("[Cinema] ワンドは、クリエイティブモードで出せます"));
            return;
        }
        int slot = player.getInventory().getSelectedSlot();
        ItemStack stack = create();
        player.getInventory().setItem(slot, stack);
        mc.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
        player.sendOverlayMessage(Component.literal("[Cinema] ワンドを入れました"));
    }

    private static InteractionResult handle(Player player, Level level, InteractionHand hand) {
        if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || !isWand(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return InteractionResult.PASS;

        UUID target = pick(player);
        NpcManager.select(target);   // リーチ外・何もない所は null: 選択を外す(=ワールド設定)
        mc.gui.setScreen(new CinemaScreen());
        return InteractionResult.SUCCESS;
    }

    /** プレイヤーのエンティティのリーチ(バニラは3ブロック)。 */
    private static double reach(Player player) {
        return player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    /** 視線上で、リーチ内にいる、いちばん近い NPC。なければ null */
    private static UUID pick(Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(reach(player)));
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (NpcData d : NpcManager.defs()) {
            Entity e = NpcManager.entityOf(d.id());
            if (e == null) continue;
            AABB box = e.getBoundingBox().inflate(0.2);
            Optional<Vec3> hit = box.clip(from, to);
            if (hit.isEmpty()) continue;
            double dist = from.distanceToSqr(hit.get());
            if (dist < bestDist) {
                bestDist = dist;
                best = d.id();
            }
        }
        return best;
    }
}