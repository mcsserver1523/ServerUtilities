package de.serverutilities;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {
    private final ServerUtilitiesPlugin plugin;
    private final EconomyManager economyManager;
    private final ServerScoreboardManager scoreboardManager;

    public PlayerListener(ServerUtilitiesPlugin plugin, EconomyManager economyManager, ServerScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyManager.ensurePlayer(event.getPlayer());
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            scoreboardManager.apply(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.isFeatureEnabled("deaths-enabled")) {
            return;
        }
        economyManager.addDeath(event.getEntity());
        scoreboardManager.apply(event.getEntity());
    }
}
