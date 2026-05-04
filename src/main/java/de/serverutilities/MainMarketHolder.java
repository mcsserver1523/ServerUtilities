package de.serverutilities;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MainMarketHolder implements InventoryHolder {
    private final boolean admin;

    public MainMarketHolder(boolean admin) {
        this.admin = admin;
    }

    public boolean admin() {
        return admin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
