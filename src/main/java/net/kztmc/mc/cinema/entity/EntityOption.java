package net.kztmc.mc.cinema.entity;

/** エンティティ固有の整数オプションの宣言。右パネルにスライダーとして出る。 */
public record EntityOption(String key, String label, int min, int max, int def) {}