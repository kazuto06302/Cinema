package net.kztmc.mc.cinema.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.kztmc.mc.cinema.Cinema;
import net.kztmc.mc.cinema.entity.EntityReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** ケープの画像(config/cinema/capes/<名前>.png)の一覧と読み込み。表(capes.csv)のケープは、ハッシュがあればダウンロードする。 */
public final class CapeLibrary {
    private CapeLibrary() {}

    /** 読み込み済みのケープ。asset は描画用、preview は背中側の面(10×16)の色(選択画面の絵に使う) */
    public record Loaded(Object asset, Identifier id, int[] preview) {}

    /** 選択画面の1項目。searchKey は検索用の小文字の文字列 */
    public record Item(String id, String label, String searchKey) {}

    /** ピクセルの色の並びが ABGR の版では true にする(選択画面の絵で、赤と青が入れ替わって見えたとき) */
    private static final boolean PIXEL_IS_ABGR = false;
    private static final long RETRY_MS = 30_000;

    private static final Map<String, Loaded> LOADED = new HashMap<>();                       // 描画スレッドだけ
    private static final Map<String, Long> UNAVAILABLE = new ConcurrentHashMap<>();          // 画像が無い・失敗した時刻
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();                   // ダウンロード中
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("cinema").resolve("capes");
    }

    /** フォルダにある画像の名前(拡張子なし) */
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

    /** 選択画面の項目: 表のケープ(表の順)+ 表に無い、自分で置いた画像 */
    public static List<Item> items() {
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CapeCatalog.Entry e : CapeCatalog.all()) {
            out.add(new Item(e.id(), e.name(), (e.id() + " " + e.name()).toLowerCase(Locale.ROOT)));
            seen.add(e.id());
        }
        for (String name : names()) {
            if (seen.add(name)) out.add(new Item(name, name, name.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /** ボタンなどに出す名前。空は「なし」 */
    public static String labelOf(String id) {
        if (id == null || id.isEmpty()) return "なし";
        return CapeCatalog.find(id).map(CapeCatalog.Entry::name).orElse(id);
    }

    /** 画像の名前から、読み込み済みのケープ。無ければ null(ハッシュがあれば、裏でダウンロードを始める) */
    public static Loaded get(String name) {
        if (name == null || name.isEmpty()) return null;
        Loaded loaded = LOADED.get(name);
        if (loaded != null) return loaded;
        Long since = UNAVAILABLE.get(name);
        if (since != null && System.currentTimeMillis() - since < RETRY_MS) return null;

        Path file = folder().resolve(name + ".png");
        if (Files.exists(file)) {
            loaded = load(name, file);
            if (loaded != null) {
                LOADED.put(name, loaded);
                UNAVAILABLE.remove(name);
                return loaded;
            }
        } else {
            CapeCatalog.find(name).filter(e -> !e.hash().isEmpty())
                    .ifPresent(e -> download(name, "https://textures.minecraft.net/texture/" + e.hash()));
        }
        UNAVAILABLE.put(name, System.currentTimeMillis());
        return null;
    }

    /** ケープのアセット(ClientAsset.Texture)。無ければ null */
    public static Object assetOf(String name) {
        Loaded loaded = get(name);
        return loaded == null ? null : loaded.asset();
    }

    /** 失敗・未取得の待ち時間を、取り消す(すぐ、もう一度確認する) */
    public static void retryFailed() {
        UNAVAILABLE.clear();
    }

    /** 表にあるのに、画像もハッシュも無いケープのID */
    public static List<String> missing() {
        return CapeCatalog.all().stream()
                .filter(e -> e.hash().isEmpty() && !Files.exists(folder().resolve(e.id() + ".png")))
                .map(CapeCatalog.Entry::id)
                .toList();
    }

    // ---- 読み込み ----

    private static Loaded load(String name, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            int[] preview = previewOf(image);
            Identifier id = Identifier.fromNamespaceAndPath("cinema", "capes/" + name.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_.-]", "_"));
            AbstractTexture texture = newTexture(id, image);
            if (texture == null) return null;
            Minecraft.getInstance().getTextureManager().register(id, texture);
            Object asset = newAsset(id);
            return asset == null ? null : new Loaded(asset, id, preview);
        } catch (IOException | RuntimeException ex) {
            Cinema.LOGGER.warn("[Cinema] could not load cape {}", file, ex);
            return null;
        }
    }

    /** 背中側の面(64×32 の画像では、左上から (1,1) の 10×16)の色を取り出す。高解像度は倍率をかける */
    private static int[] previewOf(NativeImage image) {
        int scale = Math.max(1, image.getWidth() / 64);
        int[] out = new int[10 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 10; x++) {
                int sx = Math.min(image.getWidth() - 1, (1 + x) * scale);
                int sy = Math.min(image.getHeight() - 1, (1 + y) * scale);
                int p = image.getPixel(sx, sy);
                if (PIXEL_IS_ABGR) {
                    p = (p & 0xFF00FF00) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF);
                }
                out[y * 10 + x] = p;
            }
        }
        return out;
    }

    // ---- ダウンロードと取り込み ----

    private static void download(String name, String url) {
        if (!BUSY.add(name)) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((res, err) -> {
            try {
                if (err != null || res.statusCode() != 200) {
                    Cinema.LOGGER.warn("[Cinema] could not download cape {} ({})", name,
                            err != null ? err.toString() : "HTTP " + res.statusCode());
                    return;
                }
                Files.createDirectories(folder());
                Path tmp = folder().resolve(name + ".png.tmp");
                Files.write(tmp, res.body());
                Files.move(tmp, folder().resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
                UNAVAILABLE.remove(name);
                Minecraft.getInstance().execute(() -> LOADED.remove(name));   // 上書きのとき、読み直す
                Cinema.LOGGER.info("[Cinema] downloaded cape {}", name);
            } catch (IOException ex) {
                Cinema.LOGGER.warn("[Cinema] could not save cape {}", name, ex);
            } finally {
                BUSY.remove(name);
            }
        });
    }

    /** /cinema cape <id> <プレイヤー名>: そのプレイヤーのケープを、<id>.png として取り込む */
    public static void importFromPlayer(String id, String player, Consumer<String> report) {
        Minecraft mc = Minecraft.getInstance();
        Consumer<String> say = msg -> mc.execute(() -> report.accept(msg));
        CompletableFuture.runAsync(() -> {
            try {
                Optional<GameProfile> found = mc.services().profileResolver().fetchByName(player);
                if (found.isEmpty()) {
                    say.accept("プレイヤーが見つかりません: " + player);
                    return;
                }
                String url = capeUrl(found.get());
                if (url == null) {
                    say.accept(player + " は、ケープを持っていません(または、情報を取得できませんでした)");
                    return;
                }
                UNAVAILABLE.remove(id);
                download(id, url.replace("http://", "https://"));
                // capes.csv の hash 列に貼れる
                say.accept("取り込みます: " + id + "  hash=" + url.substring(url.lastIndexOf('/') + 1));
            } catch (Throwable t) {
                Cinema.LOGGER.warn("[Cinema] cape import failed", t);
                say.accept("取り込みに失敗しました(ログを見てください)");
            }
        });
    }

    private static String capeUrl(GameProfile profile) {
        String value = texturesValue(profile);
        if (value == null) return null;
        JsonObject root = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject textures = root.getAsJsonObject("textures");
        if (textures == null || !textures.has("CAPE")) return null;
        return textures.getAsJsonObject("CAPE").get("url").getAsString();
    }

    /** プロフィールの textures プロパティの値。authlib の版で名前が違うので、両方の名前を試す */
    private static String texturesValue(GameProfile profile) {
        Object props = EntityReflect.call(profile, "properties");
        if (props == null) props = EntityReflect.call(profile, "getProperties");
        if (props == null) return null;
        if (!(EntityReflect.call(props, "get", "textures") instanceof Iterable<?> list)) return null;
        for (Object p : list) {
            Object v = EntityReflect.call(p, "value");
            if (v == null) v = EntityReflect.call(p, "getValue");
            if (v instanceof String s) return s;
        }
        return null;
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
                Cinema.LOGGER.warn("[Cinema] could not create selection_outline.json ClientAsset", ex);
            }
        }
        Cinema.LOGGER.warn("[Cinema] ClientAsset$ResourceTexture was not found");
        return null;
    }
}