package de.serverutilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

public enum MarketCategory {
    MINING("mining", "Steinbruch und Erze", Material.NETHERITE_PICKAXE),
    LUMBER("lumber", "Holzfäller", Material.IRON_AXE),
    NETHER_END("nether_end", "Nether und End", Material.NETHERRACK),
    FARMING_NATURE("farming_nature", "Farmer und Natur", Material.GOLDEN_HOE),
    WATER_FISHING("water_fishing", "Wasser und Fischen", Material.FISHING_ROD),
    COLORS_DECO("colors_deco", "Farben und Deko", Material.WHITE_DYE),
    MISCELLANEOUS("miscellaneous", "Verschiedenes", Material.CRAFTING_TABLE);

    private final String configKey;
    private final String displayName;
    private final Material icon;

    MarketCategory(String configKey, String displayName, Material icon) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public static MarketCategory guess(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (containsAny(name, "ore", "ingot", "raw_", "stone", "deepslate", "cobble", "granite", "diorite", "andesite", "coal", "diamond", "emerald", "copper", "iron", "gold", "lapis", "redstone", "quartz", "amethyst", "pickaxe")) {
            return MINING;
        }
        if (containsAny(name, "log", "wood", "planks", "sapling", "leaves", "stick", "axe", "bamboo")) {
            return LUMBER;
        }
        if (containsAny(name, "nether", "end_", "ender", "chorus", "shulker", "purpur", "basalt", "blackstone", "netherrack", "soul_", "warped", "crimson")) {
            return NETHER_END;
        }
        if (containsAny(name, "seed", "wheat", "carrot", "potato", "beetroot", "melon", "pumpkin", "flower", "grass", "dirt", "mud", "moss", "crop", "hoe", "mushroom")) {
            return FARMING_NATURE;
        }
        if (containsAny(name, "fish", "cod", "salmon", "tropical", "puffer", "kelp", "coral", "water", "prismarine", "nautilus", "trident", "fishing")) {
            return WATER_FISHING;
        }
        if (containsAny(name, "dye", "wool", "terracotta", "concrete", "glass", "banner", "candle", "carpet", "glazed", "painting", "decorated")) {
            return COLORS_DECO;
        }
        return MISCELLANEOUS;
    }

    public static MarketCategory byKey(String key) {
        for (MarketCategory category : values()) {
            if (category.configKey.equalsIgnoreCase(key)) {
                return category;
            }
        }
        return MISCELLANEOUS;
    }

    public static List<MarketCategory> ordered() {
        return new ArrayList<>(List.of(values()));
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
