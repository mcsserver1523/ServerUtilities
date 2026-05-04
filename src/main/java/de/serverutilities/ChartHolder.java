package de.serverutilities;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ChartHolder implements InventoryHolder {
    private final Material material;
    private final TimeRange range;

    public ChartHolder(Material material, TimeRange range) {
        this.material = material;
        this.range = range;
    }

    public Material material() {
        return material;
    }

    public TimeRange range() {
        return range;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
