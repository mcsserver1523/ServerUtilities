package de.serverutilities;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DataStore {
    private final ServerUtilitiesPlugin plugin;
    private final File playersFile;
    private final File historyFile;
    private final File pricesFile;
    private final FileConfiguration players;
    private final FileConfiguration history;
    private final FileConfiguration prices;
    private boolean playersDirty;
    private boolean historyDirty;
    private boolean pricesDirty;
    private boolean saveQueued;

    public DataStore(ServerUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
        this.historyFile = new File(plugin.getDataFolder(), "history.yml");
        this.pricesFile = new File(plugin.getDataFolder(), "prices.yml");
        this.players = YamlConfiguration.loadConfiguration(playersFile);
        this.history = YamlConfiguration.loadConfiguration(historyFile);
        this.prices = YamlConfiguration.loadConfiguration(pricesFile);
    }

    public FileConfiguration players() {
        return players;
    }

    public FileConfiguration history() {
        return history;
    }

    public FileConfiguration prices() {
        return prices;
    }

    public void saveAll() {
        playersDirty = false;
        historyDirty = false;
        pricesDirty = false;
        saveQueued = false;
        save(players, playersFile);
        save(history, historyFile);
        save(prices, pricesFile);
    }

    public void savePlayers() {
        playersDirty = true;
        queueSave();
    }

    public void saveHistory() {
        historyDirty = true;
        queueSave();
    }

    public void savePrices() {
        pricesDirty = true;
        queueSave();
    }

    public void flushDirty() {
        if (playersDirty) {
            save(players, playersFile);
            playersDirty = false;
        }
        if (historyDirty) {
            save(history, historyFile);
            historyDirty = false;
        }
        if (pricesDirty) {
            save(prices, pricesFile);
            pricesDirty = false;
        }
        saveQueued = false;
    }

    private void queueSave() {
        if (saveQueued || !plugin.isEnabled()) {
            return;
        }
        saveQueued = true;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::flushDirty, Math.max(1L, plugin.configInt("performance.save-delay-ticks", 100)));
    }

    private void save(FileConfiguration configuration, File file) {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Datei nicht speichern: " + file.getName(), exception);
        }
    }
}
