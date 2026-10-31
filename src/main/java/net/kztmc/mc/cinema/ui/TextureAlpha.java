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
 * テクスチャの透明度の記憶。輪郭から、「すべて透明な部分」を、除くために使う。
 * テクスチャは、リソースから、読み込んで、透明度(アルファ)だけを持つ。読めないもの(プレイヤーのスキンなど)は、判定しない。
 */
public final class TextureAlpha {
    private TextureAlpha() {}

    private record Alpha(int w, int h, byte[] data) {}

    /** 読めなかったもの(判定しない) */
    private static final Alpha UNKNOWN = new Alpha(0, 0, new byte[0]);

    /** この値より小さい透明度は、「透明」とみなす */
    private static final int MIN_ALPHA = 12;

    private static final Map<Identifier, Alpha> CACHE = new HashMap<>();

    /** 範囲(0〜1のUV)の中に、不透明な画素が1つでもあれば true。テクスチャが読めなければ true */
    public static boolean hasOpaque(Identifier texture, float minU, float minV, float maxU, float maxV) {
        Alpha alpha = CACHE.computeIfAbsent(texture, TextureAlpha::load);
        if (alpha == UNKNOWN) return true;

        int x0 = clamp((int) Math.floor(minU * alpha.w), alpha.w);
        int x1 = clamp((int) Math.ceil(maxU * alpha.w) - 1, alpha.w);
        int y0 = clamp((int) Math.floor(minV * alpha.h), alpha.h);
        int y1 = clamp((int) Math.ceil(maxV * alpha.h) - 1, alpha.h);
        for (int y = y0; y <= Math.max(y0, y1); y++) {
            for (int x = x0; x <= Math.max(x0, x1); x++) {
                if ((alpha.data[y * alpha.w + x] & 0xFF) >= MIN_ALPHA) return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static Alpha load(Identifier id) {
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
                return new Alpha(w, h, data);
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