package de.serverutilities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class EconomyManager {
    private final ServerUtilitiesPlugin plugin;
    private final DataStore dataStore;

    public EconomyManager(ServerUtilitiesPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public void ensurePlayer(Player player) {
        String path = playerPath(player.getUniqueId());
        FileConfiguration players = dataStore.players();
        if (!players.contains(path + ".balance")) {
            players.set(path + ".name", player.getName());
            BigDecimal startingBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 1000.0));
            players.set(path + ".balance", startingBalance.toPlainString());
            players.set(path + ".deaths", 0);
            if (serverBankEnabled()) {
                addServerBank(BigDecimal.valueOf(plugin.configDouble("server-bank.first-join-deposit", 1000.0)), "Erster Join: " + player.getName());
            }
            appendBalancePoint(player.getUniqueId(), startingBalance);
            dataStore.savePlayers();
        } else {
            players.set(path + ".name", player.getName());
        }
    }

    public double getBalance(UUID uuid) {
        return getBalanceExact(uuid).doubleValue();
    }

    public BigDecimal getBalanceExact(UUID uuid) {
        Object raw = dataStore.players().get(playerPath(uuid) + ".balance");
        if (raw == null) {
            return BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 1000.0));
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            return BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 1000.0));
        }
    }

    public void setBalance(OfflinePlayer player, double value) {
        setBalance(player, BigDecimal.valueOf(value));
    }

    public void setBalance(OfflinePlayer player, BigDecimal value) {
        BigDecimal next = value.max(BigDecimal.ZERO);
        dataStore.players().set(playerPath(player.getUniqueId()) + ".name", player.getName() == null ? player.getUniqueId().toString() : player.getName());
        dataStore.players().set(playerPath(player.getUniqueId()) + ".balance", next.toPlainString());
        appendBalancePoint(player.getUniqueId(), next);
        dataStore.savePlayers();
    }

    public void addBalance(OfflinePlayer player, BigDecimal amount) {
        setBalance(player, getBalanceExact(player.getUniqueId()).add(amount));
    }

    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        BigDecimal balance = getBalanceExact(player.getUniqueId());
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        setBalance(player, balance.subtract(amount));
        return true;
    }

    public boolean withdraw(Player player, double amount) {
        return withdraw(player, BigDecimal.valueOf(amount));
    }

    public void deposit(Player player, double amount) {
        setBalance(player, getBalanceExact(player.getUniqueId()).add(BigDecimal.valueOf(amount)));
    }

    public BigDecimal getServerBank() {
        Object raw = dataStore.players().get("server-bank.balance");
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    public void setServerBank(BigDecimal value) {
        if (!serverBankEnabled()) {
            return;
        }
        dataStore.players().set("server-bank.balance", value.max(BigDecimal.ZERO).toPlainString());
        dataStore.savePlayers();
    }

    public void setServerBank(BigDecimal value, String reason) {
        BigDecimal current = getServerBank();
        BigDecimal next = value.max(BigDecimal.ZERO);
        if (!serverBankEnabled()) {
            return;
        }
        dataStore.players().set("server-bank.balance", next.toPlainString());
        dataStore.savePlayers();
        BigDecimal difference = next.subtract(current);
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            recordServerBankEntry("in", difference, reason);
        } else if (difference.compareTo(BigDecimal.ZERO) < 0) {
            recordServerBankEntry("out", difference.abs(), reason);
        }
    }

    public void addServerBank(BigDecimal amount) {
        addServerBank(amount, "Einzahlung");
    }

    public void addServerBank(BigDecimal amount, String reason) {
        if (!serverBankEnabled()) {
            return;
        }
        BigDecimal value = amount.max(BigDecimal.ZERO);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        dataStore.players().set("server-bank.balance", getServerBank().add(value).toPlainString());
        recordServerBankEntry("in", value, reason);
    }

    public boolean withdrawServerBank(BigDecimal amount) {
        return withdrawServerBank(amount, "Auszahlung");
    }

    public boolean withdrawServerBank(BigDecimal amount, String reason) {
        if (!serverBankEnabled()) {
            return false;
        }
        BigDecimal current = getServerBank();
        if (current.compareTo(amount) < 0) {
            return false;
        }
        dataStore.players().set("server-bank.balance", current.subtract(amount).toPlainString());
        recordServerBankEntry("out", amount, reason);
        return true;
    }

    public List<String> getServerBankEntries(String direction) {
        return dataStore.players().getStringList("server-bank.history." + direction);
    }

    public boolean checkWeeklyServerBankDistribution() {
        if (!serverBankEnabled() || !plugin.configBool("server-bank.weekly.enabled", true)) {
            return false;
        }
        ZoneId zone = weeklyZone();
        LocalDateTime now = LocalDateTime.now(zone);
        if (now.getHour() != plugin.configInt("server-bank.weekly.hour", 0) || now.getMinute() != plugin.configInt("server-bank.weekly.minute", 0)) {
            return false;
        }
        LocalDate today = now.toLocalDate();
        if (today.getDayOfWeek() != plugin.configDay("server-bank.weekly.day", DayOfWeek.MONDAY)) {
            return false;
        }
        String todayKey = today.toString();
        if (todayKey.equals(dataStore.players().getString("server-bank.last-weekly-distribution"))) {
            return false;
        }
        distributeServerBank(todayKey);
        return true;
    }

    public void distributeServerBank(String distributionKey) {
        List<UUID> players = knownPlayers();
        if (players.isEmpty()) {
            dataStore.players().set("server-bank.last-weekly-distribution", distributionKey);
            dataStore.savePlayers();
            return;
        }
        BigDecimal total = getServerBank();
        BigDecimal share = total.divide(BigDecimal.valueOf(players.size()), 2, RoundingMode.DOWN);
        if (share.compareTo(BigDecimal.ZERO) > 0) {
            for (UUID uuid : players) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                setBalance(player, getBalanceExact(uuid).add(share));
            }
        }
        setServerBank(BigDecimal.valueOf(plugin.configDouble("server-bank.weekly.deposit-per-player-after-payout", 1000.0)).multiply(BigDecimal.valueOf(players.size())));
        if (plugin.configBool("server-bank.weekly.clear-history-after-refill", true)) {
            clearServerBankHistory();
        }
        dataStore.players().set("server-bank.last-weekly-distribution", distributionKey);
        dataStore.savePlayers();
    }

    public int getDeaths(UUID uuid) {
        return dataStore.players().getInt(playerPath(uuid) + ".deaths", 0);
    }

    public void addDeath(Player player) {
        String path = playerPath(player.getUniqueId()) + ".deaths";
        dataStore.players().set(path, getDeaths(player.getUniqueId()) + 1);
        dataStore.savePlayers();
    }

    public void setDeaths(OfflinePlayer player, int deaths) {
        dataStore.players().set(playerPath(player.getUniqueId()) + ".name", player.getName() == null ? player.getUniqueId().toString() : player.getName());
        dataStore.players().set(playerPath(player.getUniqueId()) + ".deaths", Math.max(0, deaths));
        dataStore.savePlayers();
    }

    public void recordTrade(Player player, TradeType type, Material material, int amount, double unitPrice, double total) {
        if (!plugin.isFeatureEnabled("history-enabled")) {
            return;
        }
        String path = playerPath(player.getUniqueId()) + ".entries";
        Instant now = Instant.now();
        List<String> entries = dataStore.history().getStringList(path);
        entries.add(0, now + " | " + type.name() + " | " + material.name() + " x" + amount + " | Stückpreis $" + Money.format(unitPrice) + " | Gesamt $" + Money.format(total));
        entries = pruneTradeHistory(entries, now);
        int maxEntries = plugin.configInt("history.max-trade-entries", 100);
        if (maxEntries > 0 && entries.size() > maxEntries) {
            entries = new ArrayList<>(entries.subList(0, maxEntries));
        }
        dataStore.history().set(playerPath(player.getUniqueId()) + ".name", player.getName());
        dataStore.history().set(path, entries);
        dataStore.saveHistory();
    }

    public List<String> getHistory(OfflinePlayer player) {
        String path = playerPath(player.getUniqueId()) + ".entries";
        List<String> entries = dataStore.history().getStringList(path);
        List<String> pruned = pruneTradeHistory(entries, Instant.now());
        if (pruned.size() != entries.size()) {
            dataStore.history().set(path, pruned);
            dataStore.saveHistory();
        }
        return pruned;
    }

    public List<PricePoint> getBalanceHistory(OfflinePlayer player, TimeRange range) {
        List<String> raw = dataStore.history().getStringList(playerPath(player.getUniqueId()) + ".balance-history");
        long cutoff = range == TimeRange.ALL ? Long.MIN_VALUE : System.currentTimeMillis() - range.millis();
        List<PricePoint> points = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                long time = Long.parseLong(parts[0]);
                BigDecimal balance = new BigDecimal(parts[1]);
                if (time >= cutoff) {
                    points.add(new PricePoint(time, balance.doubleValue()));
                }
            } catch (NumberFormatException ignored) {
                // Ignore hand-edited malformed entries.
            }
        }
        if (points.isEmpty()) {
            points.add(new PricePoint(System.currentTimeMillis(), getBalance(player.getUniqueId())));
        }
        return points;
    }

    private void appendBalancePoint(UUID uuid, BigDecimal balance) {
        String path = playerPath(uuid) + ".balance-history";
        List<String> history = dataStore.history().getStringList(path);
        history.add(System.currentTimeMillis() + ":" + balance.toPlainString());
        int maxPoints = plugin.configInt("history.balance-history-max-points", 500);
        if (maxPoints > 0 && history.size() > maxPoints) {
            history = new ArrayList<>(history.subList(history.size() - maxPoints, history.size()));
        }
        dataStore.history().set(path, history);
        dataStore.saveHistory();
    }

    private List<UUID> knownPlayers() {
        List<UUID> players = new ArrayList<>();
        ConfigurationSection section = dataStore.players().getConfigurationSection("players");
        if (section == null) {
            return players;
        }
        for (String key : section.getKeys(false)) {
            try {
                players.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed hand-edited player keys.
            }
        }
        return players;
    }

    private void recordServerBankEntry(String direction, BigDecimal amount, String reason) {
        String path = "server-bank.history." + direction;
        List<String> entries = dataStore.players().getStringList(path);
        entries.add(0, Instant.now() + " | $" + Money.format(amount) + " | " + reason);
        int maxEntries = plugin.configInt("server-bank.history.max-entries", 500);
        if (maxEntries > 0 && entries.size() > maxEntries) {
            entries = new ArrayList<>(entries.subList(0, maxEntries));
        }
        dataStore.players().set(path, entries);
        dataStore.savePlayers();
    }

    private void clearServerBankHistory() {
        dataStore.players().set("server-bank.history.in", new ArrayList<String>());
        dataStore.players().set("server-bank.history.out", new ArrayList<String>());
    }

    private List<String> pruneTradeHistory(List<String> entries, Instant now) {
        Instant cutoff = now.minusSeconds(Math.max(0, plugin.configInt("history.trade-retention-hours", 48)) * 3600L);
        List<String> pruned = new ArrayList<>();
        for (String entry : entries) {
            Instant timestamp = parseTradeTimestamp(entry);
            if (timestamp != null && !timestamp.isBefore(cutoff)) {
                pruned.add(entry);
            }
        }
        return pruned;
    }

    private Instant parseTradeTimestamp(String entry) {
        int separator = entry.indexOf(" | ");
        if (separator <= 0) {
            return null;
        }
        try {
            return Instant.parse(entry.substring(0, separator));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String playerPath(UUID uuid) {
        return "players." + uuid;
    }

    private boolean serverBankEnabled() {
        return plugin.isFeatureEnabled("server-bank") && plugin.configBool("server-bank.enabled", true);
    }

    private ZoneId weeklyZone() {
        try {
            return ZoneId.of(plugin.configString("server-bank.weekly.timezone", "Europe/Berlin"));
        } catch (RuntimeException exception) {
            return ZoneId.of("Europe/Berlin");
        }
    }
}
