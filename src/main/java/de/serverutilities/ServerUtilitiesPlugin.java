package de.serverutilities;

import java.time.DayOfWeek;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerUtilitiesPlugin extends JavaPlugin {
    private DataStore dataStore;
    private EconomyManager economyManager;
    private MarketManager marketManager;
    private ServerScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();

        dataStore = new DataStore(this);
        economyManager = new EconomyManager(this, dataStore);
        marketManager = new MarketManager(this, dataStore);
        scoreboardManager = new ServerScoreboardManager(this, economyManager);

        marketManager.ensureMarketDefaults();
        registerCommands();
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, economyManager, scoreboardManager), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this, economyManager, marketManager, scoreboardManager), this);
        scheduleServerBankDistribution();

        for (Player player : Bukkit.getOnlinePlayers()) {
            economyManager.ensurePlayer(player);
            scoreboardManager.apply(player);
        }
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.saveAll();
        }
    }

    private void registerCommands() {
        PluginCommandHandler handler = new PluginCommandHandler(this, economyManager, marketManager, scoreboardManager);
        for (String command : new String[]{"market", "balance", "checkbalance", "checkhistory", "tode", "checktode", "resetmarket", "sell", "sellall", "setmarket", "settings", "donate", "donateconfirm", "serverbank"}) {
            getCommand(command).setExecutor(handler);
            getCommand(command).setTabCompleter(handler);
        }
    }

    private void scheduleServerBankDistribution() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (economyManager.checkWeeklyServerBankDistribution()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    scoreboardManager.apply(player);
                }
            }
        }, 20L, 20L * 60L);
    }

    public boolean isFeatureEnabled(String key) {
        String normalized = key.endsWith("-enabled") ? key.substring(0, key.length() - "-enabled".length()) : key;
        return getConfig().getBoolean("features." + normalized, getConfig().getBoolean("settings." + key, true));
    }

    public boolean configBool(String path, boolean fallback) {
        return getConfig().getBoolean(path, fallback);
    }

    public int configInt(String path, int fallback) {
        return getConfig().getInt(path, fallback);
    }

    public double configDouble(String path, double fallback) {
        return getConfig().getDouble(path, fallback);
    }

    public String configString(String path, String fallback) {
        return getConfig().getString(path, fallback);
    }

    public DayOfWeek configDay(String path, DayOfWeek fallback) {
        try {
            return DayOfWeek.valueOf(configString(path, fallback.name()).toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private void ensureConfigDefaults() {
        FileConfiguration config = getConfig();
        setDefault(config, "features.scoreboard", true);
        setDefault(config, "features.market", true);
        setDefault(config, "features.deaths", true);
        setDefault(config, "features.history", true);
        setDefault(config, "features.donations", true);
        setDefault(config, "features.server-bank", true);
        setDefault(config, "features.settings-gui", true);
        setDefault(config, "features.market-graphs", true);
        setDefault(config, "features.balance-graphs", true);
        setDefault(config, "features.sell-gui", true);
        setDefault(config, "features.sellall", true);
        setDefault(config, "features.direct-market-commands", true);
        setDefault(config, "features.market-search", true);

        setDefault(config, "performance.save-delay-ticks", 100);

        setDefault(config, "scoreboard.title", "&6ServerUtilities");
        setDefault(config, "scoreboard.show-player", true);
        setDefault(config, "scoreboard.show-stats", true);
        setDefault(config, "scoreboard.show-money", true);
        setDefault(config, "scoreboard.show-deaths", true);
        setDefault(config, "scoreboard.show-community", true);
        setDefault(config, "scoreboard.show-server-bank", true);

        setDefault(config, "economy.starting-balance", 1000.0);
        setDefault(config, "economy.allow-negative-admin-withdraw", false);

        setDefault(config, "history.trade-retention-hours", 48);
        setDefault(config, "history.max-trade-entries", 100);
        setDefault(config, "history.default-command-amount", 25);
        setDefault(config, "history.balance-history-max-points", 500);

        setDefault(config, "server-bank.enabled", true);
        setDefault(config, "server-bank.first-join-deposit", 1000.0);
        setDefault(config, "server-bank.weekly.enabled", true);
        setDefault(config, "server-bank.weekly.day", "MONDAY");
        setDefault(config, "server-bank.weekly.hour", 0);
        setDefault(config, "server-bank.weekly.minute", 0);
        setDefault(config, "server-bank.weekly.timezone", "Europe/Berlin");
        setDefault(config, "server-bank.weekly.deposit-per-player-after-payout", 1000.0);
        setDefault(config, "server-bank.weekly.clear-history-after-refill", true);
        setDefault(config, "server-bank.history.max-entries", 500);
        setDefault(config, "server-bank.history.default-command-amount", 25);

        setDefault(config, "donations.enabled", true);
        setDefault(config, "donations.player-tax-percent", 10.0);
        setDefault(config, "donations.direct-server-bank-tax-percent", 0.0);
        setDefault(config, "donations.broadcast-server-bank-donations", true);
        setDefault(config, "donations.require-click-confirmation", true);

        setDefault(config, "market.sell-tax-percent", 10.0);
        setDefault(config, "market.price-history-max-points", 500);
        setDefault(config, "market.stabilization.enabled", true);
        setDefault(config, "market.stabilization.interval-hours", 168);
        setDefault(config, "market.stabilization.factor-percent", 10.0);
        setDefault(config, "market.gui.show-sell-tax-lore", true);

        setDefault(config, "commands.balance-enabled", true);
        setDefault(config, "commands.checkbalance-enabled", true);
        setDefault(config, "commands.checkhistory-enabled", true);
        setDefault(config, "commands.tode-enabled", true);
        setDefault(config, "commands.checktode-enabled", true);
        setDefault(config, "commands.resetmarket-enabled", true);
        setDefault(config, "commands.setmarket-enabled", true);
        setDefault(config, "commands.settings-enabled", true);
        setDefault(config, "commands.serverbank-enabled", true);
        saveConfig();
    }

    private void setDefault(FileConfiguration config, String path, Object value) {
        if (!config.isSet(path)) {
            config.set(path, value);
        }
    }
}
