package net.kztmc.mc.cinema.npc;

import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** 任意のスキンを着せられるプレイヤーNPC(クライアント専用)。 */
public class NpcPlayer extends RemotePlayer {
    private final Supplier<PlayerSkin> skin;
    private boolean showName;

    /** null=スキンの自動判定、"slim"=スリム、"wide"=ノーマル */
    private String modelName;

    // getSkin は毎フレーム呼ばれるので、作り直した結果を使い回す
    private PlayerSkin lastBase;
    private String lastModel;
    private PlayerSkin lastResult;

    public NpcPlayer(ClientLevel level, GameProfile profile, Supplier<PlayerSkin> skin) {
        super(level, profile);
        this.skin = skin;
    }

    /** スキンは取得し終わるまで既定スキンが返る。届いたら自動で切り替わる。モデルの手動指定も反映する */
    @Override
    public PlayerSkin getSkin() {
        PlayerSkin base = skin.get();
        if (modelName == null) return base;
        if (base == lastBase && modelName.equals(lastModel)) return lastResult;

        PlayerModelType type = "slim".equals(modelName) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        PlayerSkin result = base.model() == type
                ? base
                : new PlayerSkin(base.body(), base.cape(), base.elytra(), type, base.secure());
        lastBase = base;
        lastModel = modelName;
        lastResult = result;
        return result;
    }

    /** 帽子・上着・袖・ズボン・マントなど、全部位を表示する(通常は他プレイヤー分は届かず非表示になる) */
    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return true;
    }

    /** 名前札。撮影用なので初期は非表示 */
    @Override
    public boolean shouldShowName() {
        return showName;
    }

    public void setShowName(boolean showName) {
        this.showName = showName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}