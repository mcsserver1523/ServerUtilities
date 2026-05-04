package de.serverutilities;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SellHolder implements InventoryHolder {
    private boolean confirmed;

    public boolean confirmed() {
        return confirmed;
    }

    public void confirm() {
        confirmed = true;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
