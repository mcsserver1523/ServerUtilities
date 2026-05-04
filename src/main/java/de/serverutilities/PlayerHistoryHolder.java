package de.serverutilities;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PlayerHistoryHolder implements InventoryHolder {
    private final UUID uuid;
    private final TimeRange range;

    public PlayerHistoryHolder(UUID uuid, TimeRange range) {
        this.uuid = uuid;
        this.range = range;
    }

    public UUID uuid() {
        return uuid;
    }

    public TimeRange range() {
        return range;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
