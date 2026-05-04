package de.serverutilities;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CategoryHolder implements InventoryHolder {
    private final MarketCategory category;
    private final int page;
    private final boolean admin;

    public CategoryHolder(MarketCategory category, int page, boolean admin) {
        this.category = category;
        this.page = page;
        this.admin = admin;
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
