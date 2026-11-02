package net.kztmc.mc.cinema.entity.profile;

import java.util.Map;
import net.kztmc.mc.cinema.entity.EntityProfile;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.kztmc.mc.cinema.npc.NpcData;
import net.kztmc.mc.cinema.npc.NpcPlayer;
import net.kztmc.mc.cinema.npc.NpcTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * プレイヤーのNPC。ネームタグの表示・ケープの表示は、チェックボックス(固有オプション)。
 * ネームタグの内容・BelowName・ケープの持ち主の文字は、右パネルの文字入力(NpcTexts に保存)。
 */
public final class PlayerProfile extends EntityProfile {
    public static final String NAME_TAG = "nametag";
    public static final String CAPE = "cape";

    public PlayerProfile() {
        super("minecraft:player");
        humanoid();
        toggle(NAME_TAG, "ネームタグを表示", false);   // 以前の動作(非表示)を、初期値にする
        toggle(CAPE, "ケープを表示", true);
    }

    @Override
    public void applyData(Entity e, NpcData d) {
        if (!(e instanceof NpcPlayer player)) return;
        player.setNameTagVisible(d.option(NAME_TAG, 0) == 1);
        player.setNameTagText(NpcTexts.get(d.id(), NpcTexts.NAME_TAG));
        player.setBelowName(NpcTexts.get(d.id(), NpcTexts.BELOW_NAME));
        player.setCapeVisible(d.option(CAPE, 1) == 1);
        player.setCapeOwner(NpcTexts.get(d.id(), NpcTexts.CAPE_OWNER));
    }

    /** BelowName: 名前の下に出る文字(スコアボードの「名前の下」と同じ場所)を、描画用の状態へ入れる */
    @Override
    public void adjustRenderState(Entity e, Object state, float time, Map<String, Integer> options) {
        if (e instanceof NpcPlayer player && !player.belowName().isEmpty()) {
            EntityReflect.setObject(state, "scoreText", Component.literal(player.belowName()));
        }
    }
}