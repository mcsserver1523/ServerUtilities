package de.serverutilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class ServerScoreboardManager {
    private final ServerUtilitiesPlugin plugin;
    private final EconomyManager economyManager;

    public ServerScoreboardManager(ServerUtilitiesPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    public void apply(Player player) {
        if (!plugin.isFeatureEnabled("scoreboard-enabled")) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("serverutils", "dummy", color(plugin.configString("scoreboard.title", "&6ServerUtilities")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 15;
        if (plugin.configBool("scoreboard.show-player", true)) {
            objective.getScore(ChatColor.YELLOW + "Spieler").setScore(score--);
            objective.getScore(ChatColor.WHITE + "Name: " + ChatColor.AQUA + player.getName()).setScore(score--);
            objective.getScore(" ").setScore(score--);
        }
        if (plugin.configBool("scoreboard.show-stats", true)) {
            objective.getScore(ChatColor.YELLOW + "Stats").setScore(score--);
            if (plugin.configBool("scoreboard.show-money", true)) {
                objective.getScore(ChatColor.WHITE + "Geld: " + ChatColor.GREEN + "$" + Money.format(economyManager.getBalance(player.getUniqueId()))).setScore(score--);
            }
            if (plugin.configBool("scoreboard.show-deaths", true)) {
                objective.getScore(ChatColor.WHITE + "Tode: " + ChatColor.RED + economyManager.getDeaths(player.getUniqueId())).setScore(score--);
            }
            objective.getScore("  ").setScore(score--);
        }
        if (plugin.configBool("scoreboard.show-community", true)) {
            objective.getScore(ChatColor.YELLOW + "Community").setScore(score--);
            if (plugin.configBool("scoreboard.show-server-bank", true)) {
                objective.getScore(ChatColor.WHITE + "Kasse: " + ChatColor.GREEN + "$" + Money.format(economyManager.getServerBank())).setScore(score);
            }
        }

        player.setScoreboard(scoreboard);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
