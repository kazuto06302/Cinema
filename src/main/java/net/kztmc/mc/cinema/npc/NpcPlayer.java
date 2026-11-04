package net.kztmc.mc.cinema.npc;

import com.mojang.authlib.GameProfile;

import java.util.Objects;
import java.util.function.Supplier;

import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.ClientAsset.Texture;
import net.minecraft.network.chat.Component;
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
    private PlayerModelType lastType;
    private Texture lastCape;
    private PlayerSkin lastResult;

    private boolean nameTagVisible;
    private String nameTagText = "";
    private String belowName = "";
    private boolean capeVisible = true;
    private String capeOwner = "";
    private java.util.function.Supplier<PlayerSkin> capeSource;   // ケープの持ち主のスキン(null なら、自分のスキンのケープ)
    private String capeFile = "";
    private Object capeFileAsset;

    public NpcPlayer(ClientLevel level, GameProfile profile, Supplier<PlayerSkin> skin) {
        super(level, profile);
        this.skin = skin;
    }

    /** スキンは取得し終わるまで既定スキンが返る。届いたら自動で切り替わる。モデルの手動指定も反映する */
    @Override
    public PlayerSkin getSkin() {
        PlayerSkin base = skin.get();
        PlayerSkin owner = capeSource == null ? null : capeSource.get();

        // ケープ: 1. 画像ファイル  2. 持ち主のケープ(取得できたら)  3. 自分のスキンのケープ
        Texture cape;
        if (capeFileAsset instanceof Texture file) cape = file;
        else if (owner != null && owner.cape() != null) cape = owner.cape();
        else cape = base.cape();

        // モデル(腕の太さ): 指定がなければ、スキンの自動判定のまま
        PlayerModelType type = modelName == null ? base.model()
                : "slim".equals(modelName) ? PlayerModelType.SLIM : PlayerModelType.WIDE;

        // 何も変えないなら、元のスキンをそのまま返す
        if (type == base.model() && Objects.equals(cape, base.cape())) return base;

        // 毎フレーム呼ばれるので、同じ条件なら前回の結果を使い回す
        if (base == lastBase && type == lastType && Objects.equals(cape, lastCape)) return lastResult;

        lastBase = base;
        lastType = type;
        lastCape = cape;
        lastResult = new PlayerSkin(base.body(), cape, base.elytra(), type, base.secure());
        return lastResult;
    }

    public void setShowName(boolean showName) {
        this.showName = showName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public void setNameTagVisible(boolean visible) {
        this.nameTagVisible = visible;
    }

    public void setNameTagText(String text) {
        this.nameTagText = text == null ? "" : text;
    }

    public void setBelowName(String text) {
        this.belowName = text == null ? "" : text;
    }

    public String belowName() {
        return belowName;
    }

    public void setCapeVisible(boolean visible) {
        this.capeVisible = visible;
    }

    /** ケープの持ち主(ユーザー名)。空なら、自分のスキンのケープ。変わったときだけ、スキンを引き直す */
    public void setCapeOwner(String name) {
        String owner = name == null ? "" : name;
        if (owner.equals(capeOwner)) return;
        capeOwner = owner;
        capeSource = owner.isEmpty() ? null : SkinResolver.forName(owner);
    }

    /** ネームタグは、設定が「表示」のときだけ出す(以前は、常に false) */
    @Override
    public boolean shouldShowName() {
        return nameTagVisible;
    }

    /** ネームタグの内容。設定があれば、それ。なければ、ふつうの名前 */
    @Override
    public Component getDisplayName() {
        return nameTagText.isEmpty() ? super.getDisplayName() : Component.literal(nameTagText);
    }

    /** ケープの表示。ケープだけ、設定で消せる(ほかの部分は、常に表示) */
    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return part != PlayerModelPart.CAPE || capeVisible;
    }

    /**
     * マントの揺れの基準(過去の位置)を、いまの位置に合わせる。
     * マントは、「いまの位置」と「記録している過去の位置」の差で揺れる。NPCは、ティックされず、記録が原点のままなので、
     * 差が大きくなって、マントが、おかしな角度で止まる。記録を、いまの位置にして、まっすぐ垂らす。
     */
    public void settleCape() {
        for (String prefix : new String[] {"xCloak", "yCloak", "zCloak"}) {
            double value = prefix.startsWith("x") ? getX() : prefix.startsWith("y") ? getY() : getZ();
            EntityReflect.setDouble(this, prefix, value);
            EntityReflect.setDouble(this, prefix + "O", value);
        }
    }

    public void setCapeFile(String name) {
        String file = name == null ? "" : name;
        if (file.equals(capeFile)) return;
        capeFile = file;
        capeFileAsset = CapeLibrary.assetOf(file);
    }
}