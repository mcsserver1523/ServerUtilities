package de.serverutilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class MarketGui {
    private static final int[] MAIN_CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] CATEGORY_ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int CATEGORY_BACK_SLOT = 0;
    private static final int CATEGORY_NEXT_SLOT = 8;
    private static final int TRADE_BACK_SLOT = 0;
    private static final int SELL_CONFIRM_SLOT = 49;
    private static final int[] BUY_SLOTS = {12, 11, 10, 9};
    private static final int[] SELL_SLOTS = {14, 15, 16, 17};
    private static final int[] AMOUNTS = {1, 8, 32, 64};
    private static final Map<UUID, ChartSession> CHART_SESSIONS = new HashMap<>();
    private static final List<UUID> CHART_SWITCHING = new ArrayList<>();

    private MarketGui() {
    }

    public static void openMain(Player player, MarketManager marketManager, boolean admin) {
        Inventory inventory = Bukkit.createInventory(new MainMarketHolder(admin), 27, admin ? "Markt bearbeiten" : "Markt");
        fill(inventory);
        List<MarketCategory> categories = MarketCategory.ordered();
        for (int index = 0; index < categories.size(); index++) {
            MarketCategory category = categories.get(index);
            inventory.setItem(MAIN_CATEGORY_SLOTS[index], GuiItem.item(marketManager.categoryIcon(category), marketManager.categoryDisplayName(category), "Klicken zum Öffnen"));
        }
        player.openInventory(inventory);
    }

    public static void openCategory(Player player, MarketManager marketManager, MarketCategory category, int page, boolean admin) {
        Inventory inventory = Bukkit.createInventory(new CategoryHolder(category, page, admin), 36, marketManager.categoryDisplayName(category));
        fillExcept(inventory, CATEGORY_ITEM_SLOTS);
        inventory.setItem(4, GuiItem.item(marketManager.categoryIcon(category), marketManager.categoryDisplayName(category), "Kategorie"));
        inventory.setItem(CATEGORY_BACK_SLOT, GuiItem.item(Material.ARROW, page == 0 ? "Zurück" : "Seite zurück"));

        List<MarketItem> items = marketManager.itemsFor(category);
        int maxPage = Math.max(0, (items.size() - 1) / CATEGORY_ITEM_SLOTS.length);
        if (page < maxPage) {
            inventory.setItem(CATEGORY_NEXT_SLOT, GuiItem.item(Material.ARROW, "Nächste Seite"));
        }

        int offset = page * CATEGORY_ITEM_SLOTS.length;
        for (int slotIndex = 0; slotIndex < CATEGORY_ITEM_SLOTS.length; slotIndex++) {
            int itemIndex = offset + slotIndex;
            if (itemIndex >= items.size()) {
                break;
            }
            Material material = items.get(itemIndex).material();
            double price = marketManager.getPrice(material);
            inventory.setItem(CATEGORY_ITEM_SLOTS[slotIndex], GuiItem.item(material, readable(material), "Preis: $" + Money.format(price), admin ? "Admin-Modus: Item in Config bearbeiten" : "Kaufen/Verkaufen öffnen"));
        }
        player.openInventory(inventory);
    }

    public static void openTrade(Player player, MarketManager marketManager, Material material, MarketCategory category, int page, boolean admin) {
        Inventory inventory = Bukkit.createInventory(new TradeHolder(material, category, page, admin), 27, readable(material));
        fillExcept(inventory, TRADE_BACK_SLOT, 4, 9, 10, 11, 12, 13, 14, 15, 16, 17);
        inventory.setItem(TRADE_BACK_SLOT, GuiItem.item(Material.ARROW, "Zurück"));
        inventory.setItem(4, GuiItem.item(Material.MAP, "Grafik anzeigen", "Klicken für Kursdaten"));
        double unitPrice = marketManager.getPrice(material);
        double sellMultiplier = 1.0 - marketManager.sellTaxRate();
        inventory.setItem(13, GuiItem.item(material, readable(material), "Aktueller Preis: $" + Money.format(unitPrice)));

        for (int index = 0; index < AMOUNTS.length; index++) {
            int amount = AMOUNTS[index];
            double total = unitPrice * amount;
            double sellTotal = total * sellMultiplier;
            inventory.setItem(BUY_SLOTS[index], GuiItem.item(Material.GREEN_CANDLE, amount, "Kaufen x" + amount, List.of("Kosten: $" + Money.format(total))));
            List<String> sellLore = new ArrayList<>();
            sellLore.add("Erlös: $" + Money.format(sellTotal));
            if (marketManager.showSellTaxLore()) {
                sellLore.add(Money.format(marketManager.sellTaxRate() * 100.0) + "% Verkaufsabschlag");
            }
            inventory.setItem(SELL_SLOTS[index], GuiItem.item(Material.RED_CANDLE, amount, "Verkaufen x" + amount, sellLore));
        }
        player.openInventory(inventory);
    }

    public static void openChart(Player player, MarketManager marketManager, Material material, TimeRange range) {
        MarketItem item = marketManager.getItem(material);
        openChart(player, marketManager, material, range, item.category(), pageForItem(marketManager, item.category(), material), false);
    }

    public static void openChart(Player player, MarketManager marketManager, Material material, TimeRange range, MarketCategory category, int page, boolean admin) {
        boolean rising = marketManager.roseInRange(material, range);
        List<PricePoint> history = marketManager.getPriceHistory(material, range);
        MapView mapView = Bukkit.createMap(player.getWorld());
        mapView.setScale(MapView.Scale.CLOSEST);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new PriceMapRenderer(history, rising));
        player.sendMap(mapView);
        List<String> mapLore = new ArrayList<>();
        mapLore.add("Zeitraum: " + range.label());
        mapLore.add("Aktuell: $" + Money.format(marketManager.getPrice(material)));
        mapLore.add("Hoch: $" + Money.format(marketManager.highestPrice(material, range)));
        mapLore.add("Tief: $" + Money.format(marketManager.lowestPrice(material, range)));
        mapLore.addAll(textChart(history, rising));
        ItemStack map = GuiItem.mapItem(mapView, rising ? ChatColor.GREEN + "Kurs gestiegen" : ChatColor.RED + "Kurs gefallen", mapLore.toArray(String[]::new));
        ItemStack item = GuiItem.item(material, readable(material),
            "Aktueller Preis: $" + Money.format(marketManager.getPrice(material)),
            "Höchster Preis: $" + Money.format(marketManager.highestPrice(material, range)),
            "Niedrigster Preis: $" + Money.format(marketManager.lowestPrice(material, range)));
        ItemStack clock = GuiItem.item(Material.CLOCK, "Zeitraum: " + range.label(), "Klicken zum Wechseln");
        InventoryView view = MenuType.CARTOGRAPHY_TABLE.builder().title(Component.text("Grafik")).build(player);
        setCartographyViewItems(view, map, item, clock);
        player.openInventory(view);
        CHART_SESSIONS.put(player.getUniqueId(), ChartSession.item(material, range, category, page, admin));
        setCartographyViewItems(view, map, item, clock);
        player.updateInventory();
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("ServerUtilities"), () -> {
            InventoryView openView = player.getOpenInventory();
            setCartographyViewItems(openView, map, item, clock);
            player.sendMap(mapView);
            player.updateInventory();
        }, 1L);
    }

    public static void openPlayerHistory(Player viewer, EconomyManager economyManager, OfflinePlayer target, TimeRange range) {
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        List<PricePoint> history = economyManager.getBalanceHistory(target, range);
        boolean rising = history.get(history.size() - 1).price() >= history.get(0).price();
        MapView mapView = Bukkit.createMap(viewer.getWorld());
        mapView.setScale(MapView.Scale.CLOSEST);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new PriceMapRenderer(history, rising));
        viewer.sendMap(mapView);

        double current = economyManager.getBalance(target.getUniqueId());
        double highest = history.stream().mapToDouble(PricePoint::price).max().orElse(current);
        double lowest = history.stream().mapToDouble(PricePoint::price).min().orElse(current);
        List<String> mapLore = new ArrayList<>();
        mapLore.add("Zeitraum: " + range.label());
        mapLore.add("Aktuell: $" + Money.format(current));
        mapLore.add("Hoch: $" + Money.format(highest));
        mapLore.add("Tief: $" + Money.format(lowest));
        mapLore.addAll(textChart(history, rising));
        ItemStack map = GuiItem.mapItem(mapView, rising ? ChatColor.GREEN + "Kontostand gestiegen" : ChatColor.RED + "Kontostand gefallen", mapLore.toArray(String[]::new));

        List<String> lore = new ArrayList<>();
        lore.add("Kontostand: $" + Money.format(current));
        lore.add("Tode: " + economyManager.getDeaths(target.getUniqueId()));
        List<String> entries = economyManager.getHistory(target);
        if (entries.isEmpty()) {
            lore.add("Keine Trades vorhanden.");
        } else {
            lore.add("Letzte Trades:");
            entries.stream().limit(8).forEach(lore::add);
        }
        ItemStack item = GuiItem.item(Material.PLAYER_HEAD, name, lore.toArray(String[]::new));
        ItemStack clock = GuiItem.item(Material.CLOCK, "Zeitraum: " + range.label(), "Klicken zum Wechseln");
        InventoryView view = MenuType.CARTOGRAPHY_TABLE.builder().title(Component.text("Balance")).build(viewer);
        setCartographyViewItems(view, map, item, clock);
        viewer.openInventory(view);
        CHART_SESSIONS.put(viewer.getUniqueId(), ChartSession.balance(target.getUniqueId(), range));
        setCartographyViewItems(view, map, item, clock);
        viewer.updateInventory();
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("ServerUtilities"), () -> {
            InventoryView openView = viewer.getOpenInventory();
            setCartographyViewItems(openView, map, item, clock);
            viewer.sendMap(mapView);
            viewer.updateInventory();
        }, 1L);
    }

    public static void openSettings(Player player, ServerUtilitiesPlugin plugin) {
        List<String> keys = settingKeys();
        int size = Math.min(54, Math.max(27, ((keys.size() + 8) / 9) * 9));
        Inventory inventory = Bukkit.createInventory(new SettingsHolder(), size, "ServerUtilities Settings");
        fill(inventory);
        for (int index = 0; index < keys.size(); index++) {
            if (index >= inventory.getSize()) {
                break;
            }
            String key = keys.get(index);
            boolean enabled = plugin.isFeatureEnabled(key);
            inventory.setItem(index, GuiItem.item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, key, enabled ? "Aktiviert" : "Deaktiviert", "Klicken zum Umschalten"));
        }
        player.openInventory(inventory);
    }

    public static List<String> settingKeys() {
        return List.of(
            "scoreboard",
            "market",
            "deaths",
            "history",
            "donations",
            "server-bank",
            "settings-gui",
            "market-graphs",
            "balance-graphs",
            "sell-gui",
            "sellall",
            "direct-market-commands",
            "market-search"
        );
    }

    public static void openSell(Player player, MarketManager marketManager) {
        Inventory inventory = Bukkit.createInventory(new SellHolder(), 54, "Items verkaufen");
        fillSellBar(inventory, 0.0);
        player.openInventory(inventory);
    }

    public static void openSellAll(Player player, MarketManager marketManager) {
        Inventory inventory = Bukkit.createInventory(new SellHolder(), 54, "Items verkaufen");
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<ItemStack> leftovers = new ArrayList<>();
        for (int index = 0; index < contents.length; index++) {
            ItemStack content = contents[index];
            if (content == null || content.getType().isAir() || !marketManager.isSellable(content.getType())) {
                continue;
            }
            leftovers.addAll(inventory.addItem(content.clone()).values());
            contents[index] = null;
        }
        player.getInventory().setStorageContents(contents);
        leftovers.forEach(leftover -> player.getInventory().addItem(leftover));
        fillSellBar(inventory, calculateSellInventoryTotal(inventory, marketManager));
        player.openInventory(inventory);
    }

    public static boolean isSellStorageSlot(int slot) {
        return slot >= 0 && slot < 45;
    }

    public static boolean isSellConfirmSlot(int slot) {
        return slot == SELL_CONFIRM_SLOT;
    }

    public static double calculateSellInventoryTotal(Inventory inventory, MarketManager marketManager) {
        double total = 0.0;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir() && marketManager.isSellable(stack.getType())) {
                total += marketManager.getSellPrice(stack.getType(), stack.getAmount());
            }
        }
        return total;
    }

    public static void updateSellConfirm(Inventory inventory, MarketManager marketManager) {
        fillSellBar(inventory, calculateSellInventoryTotal(inventory, marketManager));
    }

    public static ChartSession chartSession(Player player) {
        return CHART_SESSIONS.get(player.getUniqueId());
    }

    public static void clearChartSession(Player player) {
        CHART_SESSIONS.remove(player.getUniqueId());
    }

    public static void markChartSwitch(Player player) {
        if (!CHART_SWITCHING.contains(player.getUniqueId())) {
            CHART_SWITCHING.add(player.getUniqueId());
        }
    }

    public static boolean consumeChartSwitch(Player player) {
        return CHART_SWITCHING.remove(player.getUniqueId());
    }

    public static int amountFromTradeSlot(int slot) {
        for (int index = 0; index < AMOUNTS.length; index++) {
            if (BUY_SLOTS[index] == slot || SELL_SLOTS[index] == slot) {
                return AMOUNTS[index];
            }
        }
        return 0;
    }

    public static TradeType tradeTypeFromSlot(int slot) {
        for (int buySlot : BUY_SLOTS) {
            if (buySlot == slot) {
                return TradeType.BUY;
            }
        }
        for (int sellSlot : SELL_SLOTS) {
            if (sellSlot == slot) {
                return TradeType.SELL;
            }
        }
        return null;
    }

    public static int categoryBackSlot() {
        return CATEGORY_BACK_SLOT;
    }

    public static int categoryNextSlot() {
        return CATEGORY_NEXT_SLOT;
    }

    public static int tradeBackSlot() {
        return TRADE_BACK_SLOT;
    }

    public static boolean hasNextCategoryPage(MarketManager marketManager, MarketCategory category, int page) {
        List<MarketItem> items = marketManager.itemsFor(category);
        int maxPage = Math.max(0, (items.size() - 1) / CATEGORY_ITEM_SLOTS.length);
        return page < maxPage;
    }

    public static MarketCategory categoryFromMainSlot(int slot) {
        List<MarketCategory> categories = MarketCategory.ordered();
        for (int index = 0; index < MAIN_CATEGORY_SLOTS.length; index++) {
            if (MAIN_CATEGORY_SLOTS[index] == slot && index < categories.size()) {
                return categories.get(index);
            }
        }
        return null;
    }

    public static int itemIndexFromCategorySlot(int slot) {
        for (int index = 0; index < CATEGORY_ITEM_SLOTS.length; index++) {
            if (CATEGORY_ITEM_SLOTS[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    public static int pageForItem(MarketManager marketManager, MarketCategory category, Material material) {
        List<MarketItem> items = marketManager.itemsFor(category);
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).material() == material) {
                return index / CATEGORY_ITEM_SLOTS.length;
            }
        }
        return 0;
    }

    public static String readable(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            words.add(part.substring(0, 1).toUpperCase() + part.substring(1));
        }
        return String.join(" ", words);
    }

    private static void fill(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, GuiItem.filler());
        }
    }

    private static void fillExcept(Inventory inventory, int... openSlots) {
        fill(inventory);
        for (int slot : openSlots) {
            inventory.clear(slot);
        }
    }

    private static void fillSellBar(Inventory inventory, double total) {
        for (int slot = 45; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, GuiItem.filler());
        }
        inventory.setItem(SELL_CONFIRM_SLOT, GuiItem.item(Material.GREEN_STAINED_GLASS_PANE, "Verkauf bestätigen", "Erlös: $" + Money.format(total)));
    }

    private static void setCartographyItems(Inventory inventory, ItemStack map, ItemStack item, ItemStack result) {
        inventory.setItem(0, map);
        inventory.setItem(1, item);
        if (inventory instanceof CartographyInventory cartographyInventory) {
            cartographyInventory.setResult(result);
        } else {
            inventory.setItem(2, result);
        }
    }

    private static void setCartographyViewItems(InventoryView view, ItemStack map, ItemStack item, ItemStack result) {
        Inventory top = view.getTopInventory();
        setCartographyItems(top, map, item, result);
        view.setItem(0, map);
        view.setItem(1, item);
        view.setItem(2, result);
    }

    private static List<String> textChart(List<PricePoint> points, boolean rising) {
        List<String> lore = new ArrayList<>();
        lore.add("Verlauf:");
        if (points.isEmpty()) {
            lore.add("Keine Daten vorhanden.");
            return lore;
        }
        double min = points.stream().mapToDouble(PricePoint::price).min().orElse(0.0);
        double max = points.stream().mapToDouble(PricePoint::price).max().orElse(min);
        double range = Math.max(0.0001, max - min);
        String blocks = "▁▂▃▄▅▆▇█";
        StringBuilder line = new StringBuilder(rising ? ChatColor.GREEN.toString() : ChatColor.RED.toString());
        int maxPoints = 24;
        int start = Math.max(0, points.size() - maxPoints);
        for (PricePoint point : points.subList(start, points.size())) {
            int index = (int) Math.round(((point.price() - min) / range) * (blocks.length() - 1));
            line.append(blocks.charAt(Math.max(0, Math.min(blocks.length() - 1, index))));
        }
        lore.add(line.toString());
        return lore;
    }

    public record ChartSession(Material material, UUID target, TimeRange range, boolean balance, MarketCategory category, int page, boolean admin) {
        public static ChartSession item(Material material, TimeRange range, MarketCategory category, int page, boolean admin) {
            return new ChartSession(material, null, range, false, category, page, admin);
        }

        public static ChartSession balance(UUID target, TimeRange range) {
            return new ChartSession(null, target, range, true, null, 0, false);
        }
    }
}
