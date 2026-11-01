package net.kztmc.mc.cinema.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * テクスチャの透明度の記憶。輪郭から、「透明な画素」を、除くために使う。
 * テクスチャは、リソースから、読み込んで、透明度(アルファ)だけを持つ。読めないもの(プレイヤーのスキンなど)は、判定しない(null)。
 */
public final class TextureAlpha {
    private TextureAlpha() {}

    /** テクスチャの透明度。(u, v) は、0〜1 のテクスチャ座標 */
    public static final class Sampler {
        private final int w;
        private final int h;
        private final byte[] data;

        private Sampler(int w, int h, byte[] data) {
            this.w = w;
            this.h = h;
            this.data = data;
        }

        /** その位置が、不透明(透明度が MIN_ALPHA 以上)か */
        public boolean opaque(float u, float v) {
            int x = Math.max(0, Math.min(w - 1, (int) Math.floor(u * w)));
            int y = Math.max(0, Math.min(h - 1, (int) Math.floor(v * h)));
            return (data[y * w + x] & 0xFF) >= MIN_ALPHA;
        }
    }

    /** この値より小さい透明度は、「透明」とみなす */
    private static final int MIN_ALPHA = 12;

    /** 読めなかったもの(判定しない) */
    private static final Sampler UNKNOWN = new Sampler(1, 1, new byte[] {(byte) 0xFF});

    private static final Map<Identifier, Sampler> CACHE = new HashMap<>();

    /** テクスチャの透明度。読めなければ(またはIDが null なら) null */
    public static Sampler sampler(Identifier texture) {
        if (texture == null) return null;
        Sampler sampler = CACHE.computeIfAbsent(texture, TextureAlpha::load);
        return sampler == UNKNOWN ? null : sampler;
    }

    private static Sampler load(Identifier id) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return UNKNOWN;
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                int w = image.getWidth();
                int h = image.getHeight();
                byte[] data = new byte[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        data[y * w + x] = (byte) (image.getPixel(x, y) >>> 24);   // 上位8ビットが透明度
                    }
                }
                return new Sampler(w, h, data);
            }
        } catch (IOException | RuntimeException ex) {
            return UNKNOWN;
        }
    }

    /** リソースの再読み込みのあとに、呼ぶ(任意) */
    public static void clear() {
        CACHE.clear();
    }
}