package net.kztmc.mc.cinema.ui.core;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** 翻訳。キーが "cinema." で始まるものは翻訳し、そうでないものは、そのまま返す(少しずつ移行できる) */
public final class Tr {
    private Tr() {}

    public static String get(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static Component c(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static String maybe(String text) {
        return text.startsWith("cinema.") ? I18n.get(text) : text;
    }
}