package de.serverutilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MarketManager {
    private static final int DEFAULT_RARITY_STOCK = 64;

    private final ServerUtilitiesPlugin plugin;
    private final DataStore dataStore;
    private Map<MarketCategory, List<MarketItem>> cachedItemsByCategory;
    private Map<Material, MarketItem> cachedItemsByMaterial;

    public MarketManager(ServerUtilitiesPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public void ensureMarketDefaults() {
        FileConfiguration config = plugin.getConfig();
        setDefault(config, "market.baseprice_high", 1000.0);
        setDefault(config, "market.baseprice_low", 1.0);
        setDefault(config, "market.price-change-percent-per-stack", 0.035);
        setDefault(config, "market.min-price", 0.01);
        setDefault(config, "market.sell-tax-percent", 10.0);
        setDefault(config, "market.price-history-max-points", 500);
        setDefault(config, "market.stabilization.enabled", true);
        setDefault(config, "market.stabilization.interval-hours", 168);
        setDefault(config, "market.stabilization.factor-percent", 10.0);
        config.set("market.items", null);
        config.set("market.reference-values", null);
        plugin.saveConfig();

        for (MarketCategory category : MarketCategory.ordered()) {
            for (MarketItem item : itemsFor(category)) {
                Material material = item.material();
                if (!dataStore.prices().contains(pricePath(material) + ".current")) {
                    double price = calculateBasePrice(material);
                    dataStore.prices().set(pricePath(material) + ".current", price);
                    dataStore.prices().set(pricePath(material) + ".last-stabilized", System.currentTimeMillis());
                    appendPricePoint(material, price);
                }
            }
        }
        dataStore.savePrices();
    }

    private void setDefault(FileConfiguration config, String path, Object value) {
        if (!config.isSet(path)) {
            config.set(path, value);
        }
    }

    public List<MarketItem> itemsFor(MarketCategory category) {
        return new ArrayList<>(itemsByCategory().getOrDefault(category, List.of()));
    }

    public List<MarketItem> allItems() {
        List<MarketItem> items = new ArrayList<>();
        for (MarketCategory category : MarketCategory.ordered()) {
            items.addAll(itemsFor(category));
        }
        return items;
    }

    public String categoryDisplayName(MarketCategory category) {
        return plugin.configString("market.categories." + category.configKey() + ".name", category.displayName());
    }

    public Material categoryIcon(MarketCategory category) {
        Material configured = Material.matchMaterial(plugin.configString("market.categories." + category.configKey() + ".icon", category.icon().name()));
        if (configured == null || !configured.isItem() || configured.isAir()) {
            return category.icon();
        }
        return configured;
    }

    public MarketItem getItem(Material material) {
        MarketItem item = itemsByMaterial().get(material);
        if (item != null) {
            return item;
        }
        return new MarketItem(material, MarketCategory.guess(material), DEFAULT_RARITY_STOCK, false);
    }

    public double getPrice(Material material) {
        if (!dataStore.prices().contains(pricePath(material) + ".current")) {
            double base = calculateBasePrice(material);
            dataStore.prices().set(pricePath(material) + ".current", base);
            dataStore.prices().set(pricePath(material) + ".last-stabilized", System.currentTimeMillis());
            appendPricePoint(material, base);
            dataStore.savePrices();
        }
        applyStabilization(material);
        return Math.max(plugin.getConfig().getDouble("market.min-price", 0.01), dataStore.prices().getDouble(pricePath(material) + ".current"));
    }

    public double getTotalPrice(Material material, int amount) {
        return getPrice(material) * amount;
    }

    public double getSellPrice(Material material, int amount) {
        return getPrice(material) * amount * (1.0 - sellTaxRate());
    }

    public double getSellTax(Material material, int amount) {
        return getPrice(material) * amount * sellTaxRate();
    }

    public double sellTaxRate() {
        return Math.max(0.0, Math.min(100.0, plugin.configDouble("market.sell-tax-percent", 10.0))) / 100.0;
    }

    public boolean showSellTaxLore() {
        return plugin.configBool("market.gui.show-sell-tax-lore", true);
    }

    public void applyTradeMovement(Material material, TradeType type, int amount) {
        double current = getPrice(material);
        double stacks = Math.max(1.0, amount) / 64.0;
        double percent = plugin.getConfig().getDouble("market.price-change-percent-per-stack", 0.035) * stacks;
        double multiplier = type == TradeType.BUY ? 1.0 + percent : 1.0 - percent;
        double minPrice = plugin.getConfig().getDouble("market.min-price", 0.01);
        double next = Math.max(minPrice, current * multiplier);
        dataStore.prices().set(pricePath(material) + ".current", next);
        dataStore.prices().set(pricePath(material) + ".last-stabilized", System.currentTimeMillis());
        appendPricePoint(material, next);
        pruneHistory(material);
        dataStore.savePrices();
    }

    public List<PricePoint> getPriceHistory(Material material, TimeRange range) {
        List<String> raw = dataStore.prices().getStringList(pricePath(material) + ".history");
        long cutoff = range == TimeRange.ALL ? Long.MIN_VALUE : System.currentTimeMillis() - range.millis();
        List<PricePoint> points = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                long time = Long.parseLong(parts[0]);
                double price = Double.parseDouble(parts[1]);
                if (time >= cutoff) {
                    points.add(new PricePoint(time, price));
                }
            } catch (NumberFormatException ignored) {
                // Ignore hand-edited malformed entries instead of breaking the market GUI.
            }
        }
        if (points.isEmpty()) {
            points.add(new PricePoint(System.currentTimeMillis(), getPrice(material)));
        }
        return points;
    }

    public double highestPrice(Material material, TimeRange range) {
        return getPriceHistory(material, range).stream().mapToDouble(PricePoint::price).max().orElse(getPrice(material));
    }

    public double lowestPrice(Material material, TimeRange range) {
        return getPriceHistory(material, range).stream().mapToDouble(PricePoint::price).min().orElse(getPrice(material));
    }

    public boolean roseInRange(Material material, TimeRange range) {
        List<PricePoint> points = getPriceHistory(material, range);
        return points.get(points.size() - 1).price() >= points.get(0).price();
    }

    public double calculateBasePrice(Material material) {
        int rarity = Math.max(1, getItem(material).rarityStock());
        double high = plugin.getConfig().getDouble("market.baseprice_high", 1000.0);
        double low = plugin.getConfig().getDouble("market.baseprice_low", 1.0);
        double price = high / rarity;
        return Math.max(low, price);
    }

    public void setItemEnabled(Material material, boolean enabled) {
        if (enabled) {
            setItemCategory(material, MarketCategory.guess(material));
            return;
        }
        FileConfiguration config = liveConfig();
        for (MarketCategory category : MarketCategory.ordered()) {
            config.set(categoryItemsPath(category) + "." + itemKey(material), null);
        }
        saveLiveConfig(config);
    }

    public void setItemCategory(Material material, MarketCategory category) {
        int rarity = Math.max(1, getItem(material).rarityStock());
        FileConfiguration config = liveConfig();
        for (MarketCategory existingCategory : MarketCategory.ordered()) {
            config.set(categoryItemsPath(existingCategory) + "." + itemKey(material), null);
        }
        config.set(categoryItemsPath(category) + "." + itemKey(material), rarity);
        saveLiveConfig(config);
    }

    public boolean isSellable(Material material) {
        return material != null && material.isItem() && !material.isAir() && getItem(material).enabled();
    }

    public void resetPrice(Material material) {
        resetPriceData(material);
        dataStore.savePrices();
    }

    public int resetAllPrices() {
        int count = 0;
        for (MarketCategory category : MarketCategory.ordered()) {
            for (MarketItem item : itemsFor(category)) {
                resetPriceData(item.material());
                count++;
            }
        }
        dataStore.savePrices();
        return count;
    }

    private void resetPriceData(Material material) {
        double price = calculateBasePrice(material);
        dataStore.prices().set(pricePath(material) + ".current", price);
        dataStore.prices().set(pricePath(material) + ".last-stabilized", System.currentTimeMillis());
        dataStore.prices().set(pricePath(material) + ".history", new ArrayList<>(List.of(System.currentTimeMillis() + ":" + price)));
    }

    private Map<MarketCategory, List<MarketItem>> itemsByCategory() {
        if (cachedItemsByCategory == null || cachedItemsByMaterial == null) {
            reloadMarketCache();
        }
        return cachedItemsByCategory;
    }

    private Map<Material, MarketItem> itemsByMaterial() {
        if (cachedItemsByCategory == null || cachedItemsByMaterial == null) {
            reloadMarketCache();
        }
        return cachedItemsByMaterial;
    }

    private void reloadMarketCache() {
        FileConfiguration config = liveConfig();
        Map<MarketCategory, List<MarketItem>> byCategory = new EnumMap<>(MarketCategory.class);
        Map<Material, MarketItem> byMaterial = new EnumMap<>(Material.class);
        for (MarketCategory category : MarketCategory.ordered()) {
            List<MarketItem> items = configuredItemsFor(config, category);
            byCategory.put(category, List.copyOf(items));
            for (MarketItem item : items) {
                byMaterial.put(item.material(), item);
            }
        }
        cachedItemsByCategory = byCategory;
        cachedItemsByMaterial = byMaterial;
    }

    private List<MarketItem> configuredItemsFor(FileConfiguration config, MarketCategory category) {
        List<MarketItem> items = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection(categoryItemsPath(category));
        if (section == null) {
            return items;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Ungültiges Markt-Item in " + category.configKey() + ": " + key);
                continue;
            }
            int rarity = Math.max(1, section.getInt(key, DEFAULT_RARITY_STOCK));
            items.add(new MarketItem(material, category, rarity, true));
        }
        return items;
    }

    private FileConfiguration liveConfig() {
        // Read the saved server config without bundled defaults, otherwise removed market items reappear.
        return YamlConfiguration.loadConfiguration(configFile());
    }

    private void saveLiveConfig(FileConfiguration config) {
        try {
            config.save(configFile());
            plugin.reloadConfig();
            reloadMarketCache();
        } catch (IOException exception) {
            plugin.getLogger().severe("Die Markt-Config konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private File configFile() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    private void applyStabilization(Material material) {
        String path = pricePath(material);
        if (!plugin.configBool("market.stabilization.enabled", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = dataStore.prices().getLong(path + ".last-stabilized", now);
        long intervalMillis = Math.max(1L, plugin.configInt("market.stabilization.interval-hours", 168)) * 60L * 60L * 1000L;
        long periods = Math.max(0L, (now - last) / intervalMillis);
        if (periods <= 0L) {
            if (!dataStore.prices().contains(path + ".last-stabilized")) {
                dataStore.prices().set(path + ".last-stabilized", now);
                dataStore.savePrices();
            }
            return;
        }
        double current = dataStore.prices().getDouble(path + ".current", calculateBasePrice(material));
        double base = calculateBasePrice(material);
        double factor = Math.max(0.0, Math.min(100.0, plugin.configDouble("market.stabilization.factor-percent", 10.0))) / 100.0;
        double multiplier = Math.pow(1.0 - factor, periods);
        double next = base + ((current - base) * multiplier);
        dataStore.prices().set(path + ".current", next);
        dataStore.prices().set(path + ".last-stabilized", last + periods * intervalMillis);
        appendPricePoint(material, next);
        pruneHistory(material);
        dataStore.savePrices();
    }

    private void appendPricePoint(Material material, double price) {
        String path = pricePath(material) + ".history";
        List<String> history = dataStore.prices().getStringList(path);
        history.add(System.currentTimeMillis() + ":" + price);
        dataStore.prices().set(path, history);
    }

    private void pruneHistory(Material material) {
        String path = pricePath(material) + ".history";
        List<String> history = dataStore.prices().getStringList(path);
        int maxPoints = plugin.configInt("market.price-history-max-points", 500);
        if (maxPoints > 0 && history.size() > maxPoints) {
            dataStore.prices().set(path, new ArrayList<>(history.subList(history.size() - maxPoints, history.size())));
        }
    }

    private String categoryItemsPath(MarketCategory category) {
        return "market.categories." + category.configKey() + ".items";
    }

    private String itemKey(Material material) {
        return material.getKey().toString().toLowerCase(Locale.ROOT);
    }

    private String pricePath(Material material) {
        return "items." + material.name();
    }
}
