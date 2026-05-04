package de.serverutilities;

import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public final class GuiItem {
    private GuiItem() {
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            if (lore.length > 0) {
                meta.setLore(Arrays.stream(lore).map(line -> ChatColor.GRAY + line).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack item(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack mapItem(MapView view, String name, String... lore) {
        ItemStack stack = item(Material.FILLED_MAP, name, lore);
        if (stack.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(view);
            meta.setMapId(view.getId());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack filler() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
