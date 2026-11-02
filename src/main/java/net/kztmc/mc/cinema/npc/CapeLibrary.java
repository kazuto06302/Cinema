package net.kztmc.mc.cinema.npc;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * ケープの画像(config/cinema/capes/*.png)の一覧と、読み込み。
 * 画像は、動的なテクスチャとして登録して、プレイヤーのスキンのケープとして使える形(ClientAsset)にして返す。
 * ClientAsset と DynamicTexture の作り方は、バージョンで違うので、リフレクションで、形に合うものを探す。
 */
public final class CapeLibrary {
    private CapeLibrary() {}

    private static final Map<String, Object> ASSETS = new HashMap<>();

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema").resolve("capes");
    }

    /** 画像の名前(拡張子なし)の一覧 */
    public static List<String> names() {
        Path dir = folder();
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException ex) {
            Cinema.LOGGER.warn("[Cinema] could not list {}", dir, ex);
        }
        return out;
    }

    /** ケープのアセット(ClientAsset.Texture)。読み込めなければ null */
    public static Object assetOf(String name) {
        if (name == null || name.isEmpty()) return null;
        return ASSETS.computeIfAbsent(name, CapeLibrary::load);
    }

    private static Object load(String name) {
        Path file = folder().resolve(name + ".png");
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("cinema", "capes/" + name.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]", "_"));
            AbstractTexture texture = newTexture(id, image);
            if (texture == null) return null;
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return newAsset(id);
        } catch (IOException | RuntimeException ex) {
            Cinema.LOGGER.warn("[Cinema] could not load cape {}", file, ex);
            return null;
        }
    }

    /** DynamicTexture を、コンストラクタの形(Supplier + 画像 / 画像のみ)に合わせて作る */
    @SuppressWarnings("unchecked")
    private static AbstractTexture newTexture(Identifier id, NativeImage image) {
        for (Constructor<?> c : DynamicTexture.class.getConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            try {
                if (types.length == 2 && types[0] == Supplier.class && types[1] == NativeImage.class) {
                    return (AbstractTexture) c.newInstance((Supplier<String>) id::toString, image);
                }
                if (types.length == 1 && types[0] == NativeImage.class) {
                    return (AbstractTexture) c.newInstance(image);
                }
            } catch (ReflectiveOperationException ex) {
                Cinema.LOGGER.warn("[Cinema] could not create DynamicTexture", ex);
            }
        }
        Cinema.LOGGER.warn("[Cinema] no usable DynamicTexture constructor was found");
        return null;
    }

    /** ClientAsset のテクスチャ(ResourceTexture)を作る。コンストラクタの形に合わせて、(ID, パス) か (ID) を試す */
    private static Object newAsset(Identifier id) {
        for (String className : List.of("net.minecraft.core.ClientAsset$ResourceTexture",
                "net.minecraft.client.resources.ClientAsset$ResourceTexture")) {
            try {
                Class<?> cls = Class.forName(className);
                for (Constructor<?> c : cls.getConstructors()) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length == 2 && types[0] == Identifier.class && types[1] == Identifier.class) {
                        return c.newInstance(id, id);
                    }
                    if (types.length == 1 && types[0] == Identifier.class) {
                        return c.newInstance(id);
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // 次のクラス名へ
            } catch (ReflectiveOperationException ex) {
                Cinema.LOGGER.warn("[Cinema] could not create a ClientAsset", ex);
            }
        }
        Cinema.LOGGER.warn("[Cinema] ClientAsset$ResourceTexture was not found");
        return null;
    }
}