package de.serverutilities;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TradeHolder implements InventoryHolder {
    private final Material material;
    private final MarketCategory category;
    private final int page;
    private final boolean admin;

    public TradeHolder(Material material, MarketCategory category, int page, boolean admin) {
        this.material = material;
        this.category = category;
        this.page = page;
        this.admin = admin;
    }

    public Material material() {
        return material;
    }

    public MarketCategory category() {
        return category;
    }

    public int page() {
        return page;
    }

    public boolean admin() {
        return admin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
