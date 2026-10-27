package net.kztmc.mc.cinema.entity;

import java.util.HashSet;
import java.util.Set;
import net.kztmc.mc.cinema.Cinema;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

/**
 * データで決まる種類(カエルの色など)の Holder を、名前から取り出す。
 * 今後、ネコ・オオカミ・ニワトリ・ウシ・ブタなどの色(これも同じ仕組み)を足すときにも使える。
 */
public final class RegistryHolders {
    private RegistryHolders() {}

    private static final Set<String> WARNED = new HashSet<>();

    public static <T> Holder<T> get(Entity e, ResourceKey<? extends Registry<T>> registry, String id) {
        return get(e.registryAccess(), registry, id);
    }

    public static <T> Holder<T> get(RegistryAccess access, ResourceKey<? extends Registry<T>> registry, String id) {
        try {
            return access.lookupOrThrow(registry)
                    .getOrThrow(ResourceKey.create(registry, Identifier.withDefaultNamespace(id)));
        } catch (RuntimeException ex) {
            if (WARNED.add(registry.identifier() + "/" + id)) {
                Cinema.LOGGER.warn("[Cinema] registry entry {} / {} was not found", registry.identifier(), id, ex);
            }
            return null;
        }
    }
}