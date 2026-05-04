package de.serverutilities;

import org.bukkit.Material;

public record MarketItem(Material material, MarketCategory category, int rarityStock, boolean enabled) {
}
