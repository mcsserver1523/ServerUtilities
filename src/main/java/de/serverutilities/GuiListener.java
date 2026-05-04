package de.serverutilities;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiListener implements Listener {
    private final ServerUtilitiesPlugin plugin;
    private final EconomyManager economyManager;
    private final MarketManager marketManager;
    private final ServerScoreboardManager scoreboardManager;

    public GuiListener(ServerUtilitiesPlugin plugin, EconomyManager economyManager, MarketManager marketManager, ServerScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.marketManager = marketManager;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SellHolder) {
            if (!marketManager.isSellable(event.getOldCursor().getType()) || event.getRawSlots().stream().anyMatch(slot -> slot < event.getInventory().getSize() && !MarketGui.isSellStorageSlot(slot))) {
                event.setCancelled(true);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> MarketGui.updateSellConfirm(event.getInventory(), marketManager));
            return;
        }
        if (isPluginHolder(holder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SellHolder sellHolder && !sellHolder.confirmed() && event.getPlayer() instanceof Player player) {
            returnSellItems(player, event.getInventory());
        }
        if (event.getPlayer() instanceof Player player) {
            MarketGui.ChartSession chartSession = MarketGui.chartSession(player);
            if (chartSession != null) {
                event.getInventory().clear();
                plugin.getServer().getScheduler().runTask(plugin, () -> removeChartItems(player));
                if (!MarketGui.consumeChartSwitch(player) && !chartSession.balance()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> MarketGui.openTrade(player, marketManager, chartSession.material(), chartSession.category(), chartSession.page(), chartSession.admin()));
                }
            }
            MarketGui.clearChartSession(player);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        MarketGui.ChartSession chartSession = MarketGui.chartSession(player);
        if (chartSession != null && event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CARTOGRAPHY) {
            event.setCancelled(true);
            if (event.getRawSlot() == 2) {
                event.getView().getTopInventory().clear();
                MarketGui.markChartSwitch(player);
                if (chartSession.balance()) {
                    MarketGui.openPlayerHistory(player, economyManager, plugin.getServer().getOfflinePlayer(chartSession.target()), chartSession.range().next());
                } else {
                    MarketGui.openChart(player, marketManager, chartSession.material(), chartSession.range().next(), chartSession.category(), chartSession.page(), chartSession.admin());
                }
            }
            return;
        }
        if (!isPluginHolder(holder)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }
        if (!event.getClickedInventory().equals(inventory)) {
            if (holder instanceof SellHolder && event.isShiftClick() && event.getCurrentItem() != null && !marketManager.isSellable(event.getCurrentItem().getType())) {
                event.setCancelled(true);
            }
            if (holder instanceof SellHolder) {
                plugin.getServer().getScheduler().runTask(plugin, () -> MarketGui.updateSellConfirm(inventory, marketManager));
            }
            if (holder instanceof CategoryHolder categoryHolder && categoryHolder.admin() && event.isShiftClick()) {
                event.setCancelled(true);
                addItemToCategory(player, categoryHolder, event.getCurrentItem(), false);
            }
            return;
        }

        event.setCancelled(true);
        int slot = event.getSlot();
        if (holder instanceof MainMarketHolder mainMarketHolder) {
            handleMain(player, slot, mainMarketHolder);
        } else if (holder instanceof CategoryHolder categoryHolder) {
            handleCategory(event, player, slot, categoryHolder);
        } else if (holder instanceof TradeHolder tradeHolder) {
            handleTrade(player, slot, tradeHolder);
        } else if (holder instanceof ChartHolder chartHolder) {
            handleChart(player, slot, chartHolder);
        } else if (holder instanceof PlayerHistoryHolder historyHolder) {
            handlePlayerHistory(player, slot, historyHolder);
        } else if (holder instanceof SellHolder sellHolder) {
            handleSell(event, player, slot, sellHolder);
        } else if (holder instanceof SettingsHolder) {
            handleSettings(player, slot);
        }
    }

    private void handleMain(Player player, int slot, MainMarketHolder holder) {
        MarketCategory category = MarketGui.categoryFromMainSlot(slot);
        if (category != null) {
            MarketGui.openCategory(player, marketManager, category, 0, holder.admin());
        }
    }

    private void handleCategory(InventoryClickEvent event, Player player, int slot, CategoryHolder holder) {
        if (slot == MarketGui.categoryBackSlot()) {
            if (holder.page() == 0) {
                MarketGui.openMain(player, marketManager, holder.admin());
            } else {
                MarketGui.openCategory(player, marketManager, holder.category(), holder.page() - 1, holder.admin());
            }
            return;
        }
        if (slot == MarketGui.categoryNextSlot() && MarketGui.hasNextCategoryPage(marketManager, holder.category(), holder.page())) {
            MarketGui.openCategory(player, marketManager, holder.category(), holder.page() + 1, holder.admin());
            return;
        }
        int slotIndex = MarketGui.itemIndexFromCategorySlot(slot);
        if (slotIndex < 0) {
            return;
        }
        if (holder.admin()) {
            handleAdminMarketSlot(event, player, holder);
            return;
        }
        int itemIndex = holder.page() * 14 + slotIndex;
        List<MarketItem> items = marketManager.itemsFor(holder.category());
        if (itemIndex >= items.size()) {
            return;
        }
        MarketGui.openTrade(player, marketManager, items.get(itemIndex).material(), holder.category(), holder.page(), holder.admin());
    }

    private void handleTrade(Player player, int slot, TradeHolder holder) {
        if (slot == MarketGui.tradeBackSlot()) {
            MarketGui.openCategory(player, marketManager, holder.category(), holder.page(), holder.admin());
            return;
        }
        if (slot == 4) {
            if (!plugin.isFeatureEnabled("market-graphs")) {
                player.sendMessage(ChatColor.RED + "Marktgrafiken sind deaktiviert.");
                return;
            }
            MarketGui.openChart(player, marketManager, holder.material(), TimeRange.DAY, holder.category(), holder.page(), holder.admin());
            return;
        }
        TradeType type = MarketGui.tradeTypeFromSlot(slot);
        int amount = MarketGui.amountFromTradeSlot(slot);
        if (type == null || amount <= 0) {
            return;
        }

        Material material = holder.material();
        if (type == TradeType.BUY) {
            double unitPrice = marketManager.getPrice(material);
            double total = unitPrice * amount;
            buy(player, material, amount, unitPrice, total);
        } else {
            double unitPrice = marketManager.getSellPrice(material, 1);
            double total = marketManager.getSellPrice(material, amount);
            sell(player, material, amount, unitPrice, total);
        }
        scoreboardManager.apply(player);
        MarketGui.openTrade(player, marketManager, material, holder.category(), holder.page(), holder.admin());
    }

    private void buy(Player player, Material material, int amount, double unitPrice, double total) {
        if (!hasInventorySpace(player, material, amount)) {
            player.sendMessage(ChatColor.RED + "Dein Inventar ist voll.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (!economyManager.withdraw(player, total)) {
            player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        addItems(player, material, amount);
        marketManager.applyTradeMovement(material, TradeType.BUY, amount);
        economyManager.recordTrade(player, TradeType.BUY, material, amount, unitPrice, total);
        player.sendMessage(ChatColor.GREEN + "Gekauft: " + amount + "x " + MarketGui.readable(material) + " für $" + Money.format(total));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    private void sell(Player player, Material material, int amount, double unitPrice, double total) {
        if (countMaterial(player, material) < amount) {
            player.sendMessage(ChatColor.RED + "Du hast davon nicht genug Items im Inventar.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        removeMaterial(player, material, amount);
        economyManager.deposit(player, total);
        economyManager.addServerBank(java.math.BigDecimal.valueOf(marketManager.getSellTax(material, amount)), "Verkaufssteuer: " + player.getName() + " " + material.name() + " x" + amount);
        marketManager.applyTradeMovement(material, TradeType.SELL, amount);
        economyManager.recordTrade(player, TradeType.SELL, material, amount, unitPrice, total);
        player.sendMessage(ChatColor.GREEN + "Verkauft: " + amount + "x " + MarketGui.readable(material) + " für $" + Money.format(total));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        refreshScoreboards();
    }

    private void handleChart(Player player, int slot, ChartHolder holder) {
        if (slot == 2) {
            MarketGui.openChart(player, marketManager, holder.material(), holder.range().next());
        }
    }

    private void handlePlayerHistory(Player player, int slot, PlayerHistoryHolder holder) {
        if (slot == 2) {
            MarketGui.openPlayerHistory(player, economyManager, plugin.getServer().getOfflinePlayer(holder.uuid()), holder.range().next());
        }
    }

    private void handleSell(InventoryClickEvent event, Player player, int slot, SellHolder holder) {
        if (MarketGui.isSellConfirmSlot(slot)) {
            confirmSell(player, event.getInventory(), holder);
            return;
        }
        if (!MarketGui.isSellStorageSlot(slot)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && !marketManager.isSellable(cursor.getType())) {
            player.sendMessage(ChatColor.RED + "Dieses Item kann nicht auf dem Markt verkauft werden.");
            return;
        }
        event.setCancelled(false);
        plugin.getServer().getScheduler().runTask(plugin, () -> MarketGui.updateSellConfirm(event.getInventory(), marketManager));
    }

    private void handleSettings(Player player, int slot) {
        List<String> keys = MarketGui.settingKeys();
        int index = slot;
        if (index < 0 || index >= keys.size()) {
            return;
        }
        String key = keys.get(index);
        boolean current = plugin.isFeatureEnabled(key);
        plugin.getConfig().set("features." + key, !current);
        plugin.saveConfig();
        player.sendMessage(ChatColor.YELLOW + key + ": " + (!current ? "aktiviert" : "deaktiviert"));
        scoreboardManager.apply(player);
        MarketGui.openSettings(player, plugin);
    }

    private boolean isPluginHolder(InventoryHolder holder) {
        return holder instanceof MainMarketHolder
            || holder instanceof CategoryHolder
            || holder instanceof TradeHolder
            || holder instanceof ChartHolder
            || holder instanceof PlayerHistoryHolder
            || holder instanceof SellHolder
            || holder instanceof SettingsHolder;
    }

    private void confirmSell(Player player, Inventory inventory, SellHolder holder) {
        double total = MarketGui.calculateSellInventoryTotal(inventory, marketManager);
        if (total <= 0.0) {
            player.sendMessage(ChatColor.RED + "Es liegen keine verkaufbaren Items im Verkaufsfenster.");
            return;
        }
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir() || !marketManager.isSellable(stack.getType())) {
                continue;
            }
            double unitPrice = marketManager.getSellPrice(stack.getType(), 1);
            double stackTotal = marketManager.getSellPrice(stack.getType(), stack.getAmount());
            economyManager.addServerBank(java.math.BigDecimal.valueOf(marketManager.getSellTax(stack.getType(), stack.getAmount())), "Verkaufssteuer: " + player.getName() + " " + stack.getType().name() + " x" + stack.getAmount());
            marketManager.applyTradeMovement(stack.getType(), TradeType.SELL, stack.getAmount());
            economyManager.recordTrade(player, TradeType.SELL, stack.getType(), stack.getAmount(), unitPrice, stackTotal);
            inventory.clear(slot);
        }
        economyManager.deposit(player, total);
        refreshScoreboards();
        holder.confirm();
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Verkauft für $" + Money.format(total));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
    }

    private void returnSellItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(stack).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            inventory.clear(slot);
        }
    }

    private void handleAdminMarketSlot(InventoryClickEvent event, Player player, CategoryHolder holder) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType().isItem() && !cursor.getType().isAir()) {
            addItemToCategory(player, holder, cursor, true);
            consumeOneCursorItem(event, cursor);
            return;
        }

        int slotIndex = MarketGui.itemIndexFromCategorySlot(event.getSlot());
        int itemIndex = holder.page() * 14 + slotIndex;
        List<MarketItem> items = marketManager.itemsFor(holder.category());
        if (itemIndex >= items.size()) {
            return;
        }

        Material material = items.get(itemIndex).material();
        marketManager.setItemEnabled(material, false);
        if (!event.isRightClick()) {
            event.setCursor(new ItemStack(material, 1));
        }
        player.sendMessage(ChatColor.YELLOW + MarketGui.readable(material) + " wurde aus dem Markt entfernt.");
        MarketGui.openCategory(player, marketManager, holder.category(), holder.page(), true);
    }

    private void addItemToCategory(Player player, CategoryHolder holder, ItemStack stack, boolean reopen) {
        if (stack == null || !stack.getType().isItem() || stack.getType().isAir() || stack.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }
        marketManager.setItemCategory(stack.getType(), holder.category());
        player.sendMessage(ChatColor.GREEN + MarketGui.readable(stack.getType()) + " wurde zu " + marketManager.categoryDisplayName(holder.category()) + " hinzugefügt.");
        if (reopen) {
            MarketGui.openCategory(player, marketManager, holder.category(), holder.page(), true);
        }
    }

    private void consumeOneCursorItem(InventoryClickEvent event, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            event.setCursor(null);
            return;
        }
        ItemStack next = cursor.clone();
        next.setAmount(cursor.getAmount() - 1);
        event.setCursor(next);
    }

    private boolean hasInventorySpace(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStackSize = material.getMaxStackSize();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                remaining -= maxStackSize;
            } else if (content.getType() == material && content.getAmount() < content.getMaxStackSize()) {
                remaining -= content.getMaxStackSize() - content.getAmount();
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void addItems(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStackSize = material.getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            player.getInventory().addItem(new ItemStack(material, stackAmount)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= stackAmount;
        }
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.getType() == material) {
                count += content.getAmount();
            }
        }
        return count;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack content = contents[index];
            if (content == null || content.getType() != material) {
                continue;
            }
            int removed = Math.min(content.getAmount(), remaining);
            content.setAmount(content.getAmount() - removed);
            remaining -= removed;
            if (content.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private void removeChartItems(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length; index++) {
            ItemStack content = contents[index];
            if (content != null && isChartGuiItem(content)) {
                contents[index] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean isChartGuiItem(ItemStack stack) {
        if (stack.getType() != Material.FILLED_MAP && stack.getType() != Material.CLOCK) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String name = ChatColor.stripColor(meta.getDisplayName());
        return "Kurs gestiegen".equals(name)
            || "Kurs gefallen".equals(name)
            || "Kontostand gestiegen".equals(name)
            || "Kontostand gefallen".equals(name)
            || name.startsWith("Zeitraum: ");
    }

    private void refreshScoreboards() {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            scoreboardManager.apply(online);
        }
    }
}
