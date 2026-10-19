package net.kztmc.mc.cinema.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * NPCの見た目の設定をまとめたもの。NpcData の中に、同じ階層のキーとして保存される
 * (model / pose / anim_tick / options)。
 */
public record NpcStyle(
        Optional<String> model,       // player の腕の太さ。"slim" | "wide"。空なら自動
        Optional<String> pose,        // NpcPose の名前
        Optional<Float> animTick,     // アニメーション時刻(tick)。0.1刻み。空なら種類ごとの初期値
        Map<String, Integer> options  // エンティティ固有の値(スライムのサイズなど)
) {
    public static final NpcStyle EMPTY = new NpcStyle(Optional.empty(), Optional.empty(), Optional.empty(), Map.of());

    public static final MapCodec<NpcStyle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("model").forGetter(NpcStyle::model),
            Codec.STRING.optionalFieldOf("pose").forGetter(NpcStyle::pose),
            Codec.FLOAT.optionalFieldOf("anim_tick").forGetter(NpcStyle::animTick),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("options", Map.of()).forGetter(NpcStyle::options)
    ).apply(i, NpcStyle::new));

    public NpcStyle withModel(Optional<String> v) {
        return new NpcStyle(v, pose, animTick, options);
    }

    public NpcStyle withPose(String v) {
        return new NpcStyle(model, Optional.of(v), animTick, options);
    }

    public NpcStyle withAnimTick(float v) {
        return new NpcStyle(model, pose, Optional.of(v), options);
    }

    public NpcStyle withOption(String key, int value) {
        Map<String, Integer> copy = new HashMap<>(options);
        copy.put(key, value);
        return new NpcStyle(model, pose, animTick, Map.copyOf(copy));
    }
}