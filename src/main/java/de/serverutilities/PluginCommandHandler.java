package de.serverutilities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PluginCommandHandler implements CommandExecutor, TabCompleter {
    private final ServerUtilitiesPlugin plugin;
    private final EconomyManager economyManager;
    private final MarketManager marketManager;
    private final ServerScoreboardManager scoreboardManager;
    private final Map<UUID, DonationRequest> pendingDonations = new HashMap<>();

    public PluginCommandHandler(ServerUtilitiesPlugin plugin, EconomyManager economyManager, MarketManager marketManager, ServerScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.marketManager = marketManager;
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase()) {
            case "market" -> market(sender, args);
            case "balance" -> balance(sender, args);
            case "checkbalance" -> checkBalance(sender, args);
            case "checkhistory" -> checkHistory(sender, args);
            case "tode" -> deaths(sender, args);
            case "checktode" -> checkDeaths(sender, args);
            case "resetmarket" -> resetMarket(sender, args);
            case "sell" -> sell(sender);
            case "sellall" -> sellAll(sender);
            case "setmarket" -> setMarket(sender);
            case "settings" -> settings(sender);
            case "donate" -> donate(sender, args);
            case "donateconfirm" -> donateConfirm(sender, args);
            case "serverbank" -> serverBank(sender, args);
            default -> false;
        };
    }

    private boolean market(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        if (!plugin.isFeatureEnabled("market-enabled")) {
            player.sendMessage(ChatColor.RED + "Der Markt ist aktuell deaktiviert.");
            return true;
        }
        if (args.length == 0) {
            MarketGui.openMain(player, marketManager, false);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            if (!plugin.isFeatureEnabled("market-search")) {
                player.sendMessage(ChatColor.RED + "Die Marktsuche ist deaktiviert.");
                return true;
            }
            Material material = Material.matchMaterial(args[1]);
            if (material == null || !marketManager.isSellable(material)) {
                player.sendMessage(ChatColor.RED + "Dieses Item ist nicht im Markt verfügbar.");
                return true;
            }
            MarketItem item = marketManager.getItem(material);
            MarketGui.openTrade(player, marketManager, material, item.category(), MarketGui.pageForItem(marketManager, item.category(), material), false);
            return true;
        }
        if (args.length != 3 || (!args[0].equalsIgnoreCase("buy") && !args[0].equalsIgnoreCase("sell"))) {
            player.sendMessage(ChatColor.RED + "Nutzung: /market [buy|sell] <item> <anzahl> oder /market search <item>");
            return true;
        }
        if (!plugin.isFeatureEnabled("direct-market-commands")) {
            player.sendMessage(ChatColor.RED + "Direkte Markt-Befehle sind deaktiviert.");
            return true;
        }
        Material material = Material.matchMaterial(args[1]);
        if (material == null || !marketManager.isSellable(material)) {
            player.sendMessage(ChatColor.RED + "Dieses Item ist nicht im Markt verfügbar.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Bitte gib eine gültige Anzahl an.");
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Die Anzahl muss größer als 0 sein.");
            return true;
        }
        if (args[0].equalsIgnoreCase("buy")) {
            commandBuy(player, material, amount);
        } else {
            commandSell(player, material, amount);
        }
        scoreboardManager.apply(player);
        return true;
    }

    private void commandBuy(Player player, Material material, int amount) {
        double unitPrice = marketManager.getPrice(material);
        double total = unitPrice * amount;
        if (!hasInventorySpace(player, material, amount)) {
            player.sendMessage(ChatColor.RED + "Dein Inventar ist voll.");
            return;
        }
        if (!economyManager.withdraw(player, total)) {
            player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld.");
            return;
        }
        addItems(player, material, amount);
        marketManager.applyTradeMovement(material, TradeType.BUY, amount);
        economyManager.recordTrade(player, TradeType.BUY, material, amount, unitPrice, total);
        player.sendMessage(ChatColor.GREEN + "Gekauft: " + amount + "x " + MarketGui.readable(material) + " für $" + Money.format(total));
    }

    private void commandSell(Player player, Material material, int amount) {
        if (countMaterial(player, material) < amount) {
            player.sendMessage(ChatColor.RED + "Du hast davon nicht genug Items im Inventar.");
            return;
        }
        double unitPrice = marketManager.getSellPrice(material, 1);
        double total = marketManager.getSellPrice(material, amount);
        removeMaterial(player, material, amount);
        economyManager.deposit(player, total);
        economyManager.addServerBank(BigDecimal.valueOf(marketManager.getSellTax(material, amount)), "Verkaufssteuer: " + player.getName() + " " + material.name() + " x" + amount);
        marketManager.applyTradeMovement(material, TradeType.SELL, amount);
        economyManager.recordTrade(player, TradeType.SELL, material, amount, unitPrice, total);
        player.sendMessage(ChatColor.GREEN + "Verkauft: " + amount + "x " + MarketGui.readable(material) + " für $" + Money.format(total));
        refreshScoreboards();
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

    private boolean balance(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "balance")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length != 3 && args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /balance <player> <set|add|withdraw|remove> <amount>");
            return true;
        }
        String operation = args.length == 2 ? "set" : args[1].toLowerCase();
        BigDecimal value;
        try {
            value = new BigDecimal(args.length == 2 ? args[1] : args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Bitte gib eine gültige Zahl an.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!applyMoneyOperation(sender, target, operation, value)) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /balance <player> <set|add|withdraw|remove> <amount>");
            return true;
        }
        if (target.getPlayer() != null) {
            scoreboardManager.apply(target.getPlayer());
        }
        if (sender instanceof Player admin) {
            scoreboardManager.apply(admin);
        }
        sender.sendMessage(ChatColor.GREEN + "Kontostand von " + target.getName() + ": $" + Money.format(economyManager.getBalanceExact(target.getUniqueId())));
        return true;
    }

    private boolean checkBalance(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "checkbalance")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /checkbalance <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!plugin.isFeatureEnabled("balance-graphs")) {
            sender.sendMessage(ChatColor.GOLD + "Kontostand von " + target.getName() + ": $" + Money.format(economyManager.getBalanceExact(target.getUniqueId())));
            return true;
        }
        MarketGui.openPlayerHistory(player, economyManager, target, TimeRange.DAY);
        return true;
    }

    private boolean checkHistory(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "checkhistory")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /checkhistory <player> [amount]");
            return true;
        }
        int amount = plugin.configInt("history.default-command-amount", 25);
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(ChatColor.RED + "Bitte gib eine gültige Anzahl an.");
                return true;
            }
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Die Anzahl muss größer als 0 sein.");
                return true;
            }
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        List<String> history = economyManager.getHistory(target);
        sender.sendMessage(ChatColor.GOLD + "Letzte " + amount + " Trades von " + target.getName() + " aus den letzten " + plugin.configInt("history.trade-retention-hours", 48) + " Stunden:");
        if (history.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Einträge vorhanden.");
            return true;
        }
        history.stream().limit(amount).forEach(entry -> sender.sendMessage(ChatColor.GRAY + entry));
        return true;
    }

    private boolean deaths(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "tode")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /tode <spieler> <value>");
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Bitte gib eine gültige ganze Zahl an.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        economyManager.setDeaths(target, value);
        if (target.getPlayer() != null) {
            scoreboardManager.apply(target.getPlayer());
        }
        sender.sendMessage(ChatColor.GREEN + "Tode von " + target.getName() + " wurden auf " + Math.max(0, value) + " gesetzt.");
        return true;
    }

    private boolean checkDeaths(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "checktode")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /checktode <spieler>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        sender.sendMessage(ChatColor.GOLD + "Tode von " + target.getName() + ": " + ChatColor.RED + economyManager.getDeaths(target.getUniqueId()));
        return true;
    }

    private boolean resetMarket(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "resetmarket")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /resetmarket [item]");
            return true;
        }
        if (args.length == 0) {
            int count = marketManager.resetAllPrices();
            sender.sendMessage(ChatColor.GREEN + "Marktpreise wurden zurückgesetzt: " + count + " Items.");
            return true;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isItem() || material.isAir()) {
            sender.sendMessage(ChatColor.RED + "Dieses Item existiert nicht.");
            return true;
        }
        marketManager.resetPrice(material);
        sender.sendMessage(ChatColor.GREEN + "Preis zurückgesetzt: " + MarketGui.readable(material));
        return true;
    }

    private boolean sell(CommandSender sender) {
        if (!plugin.isFeatureEnabled("sell-gui")) {
            sender.sendMessage(ChatColor.RED + "Das Verkaufsfenster ist deaktiviert.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        if (!plugin.isFeatureEnabled("market-enabled")) {
            player.sendMessage(ChatColor.RED + "Der Markt ist aktuell deaktiviert.");
            return true;
        }
        MarketGui.openSell(player, marketManager);
        return true;
    }

    private boolean sellAll(CommandSender sender) {
        if (!plugin.isFeatureEnabled("sellall")) {
            sender.sendMessage(ChatColor.RED + "/sellall ist deaktiviert.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        if (!plugin.isFeatureEnabled("market-enabled")) {
            player.sendMessage(ChatColor.RED + "Der Markt ist aktuell deaktiviert.");
            return true;
        }
        MarketGui.openSellAll(player, marketManager);
        return true;
    }

    private boolean setMarket(CommandSender sender) {
        if (!commandEnabled(sender, "setmarket")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        MarketGui.openMain(player, marketManager, true);
        player.sendMessage(ChatColor.YELLOW + "Admin-Modus: Item mit Cursor in Markt-Slot legen, Linksklick nimmt ein Markt-Item heraus, Rechtsklick entfernt es.");
        return true;
    }

    private boolean settings(CommandSender sender) {
        if (!commandEnabled(sender, "settings") || !plugin.isFeatureEnabled("settings-gui")) {
            sender.sendMessage(ChatColor.RED + "Die Einstellungen sind deaktiviert.");
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.YELLOW + "Settings in config.yml: settings.scoreboard-enabled, market-enabled, deaths-enabled, history-enabled");
            return true;
        }
        MarketGui.openSettings(player, plugin);
        return true;
    }

    private boolean donate(CommandSender sender, String[] args) {
        if (!plugin.isFeatureEnabled("donations") || !plugin.configBool("donations.enabled", true)) {
            sender.sendMessage(ChatColor.RED + "Spenden sind deaktiviert.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("serverkasse")) {
            BigDecimal amount = parsePositiveAmount(player, args[1]);
            if (amount == null) {
                return true;
            }
            sendDonationConfirmation(player, DonationRequest.serverBank(amount));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("player")) {
            BigDecimal amount = parsePositiveAmount(player, args[2]);
            if (amount == null) {
                return true;
            }
            sendDonationConfirmation(player, DonationRequest.player(Bukkit.getOfflinePlayer(args[1]).getUniqueId(), args[1], amount));
            return true;
        }
        if (args.length == 2) {
            BigDecimal amount = parsePositiveAmount(player, args[1]);
            if (amount == null) {
                return true;
            }
            sendDonationConfirmation(player, DonationRequest.player(Bukkit.getOfflinePlayer(args[0]).getUniqueId(), args[0], amount));
            return true;
        }
        player.sendMessage(ChatColor.RED + "Nutzung: /donate <player> <name> <amount> oder /donate <serverkasse> <amount>");
        return true;
    }

    private boolean donateConfirm(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }
        if (args.length != 1) {
            return true;
        }
        DonationRequest request = pendingDonations.remove(player.getUniqueId());
        if (request == null || !request.token().equals(args[0])) {
            player.sendMessage(ChatColor.RED + "Diese Spenden-Bestätigung ist nicht mehr gültig.");
            return true;
        }
        if (!economyManager.withdraw(player, request.amount())) {
            player.sendMessage(ChatColor.RED + "Du hast nicht genug Geld.");
            return true;
        }
        if (request.serverBank()) {
            BigDecimal bankShare = request.amount().multiply(percent("donations.direct-server-bank-tax-percent", 0.0));
            BigDecimal serverAmount = request.amount().subtract(bankShare).add(bankShare);
            economyManager.addServerBank(serverAmount, "Spende an Serverkasse: " + player.getName());
            if (plugin.configBool("donations.broadcast-server-bank-donations", true)) {
                Bukkit.broadcastMessage(ChatColor.GOLD + player.getName() + " hat $" + Money.format(serverAmount) + " an die Serverkasse gespendet.");
            }
        } else {
            BigDecimal bankShare = request.amount().multiply(percent("donations.player-tax-percent", 10.0));
            BigDecimal playerShare = request.amount().subtract(bankShare);
            OfflinePlayer target = Bukkit.getOfflinePlayer(request.target());
            economyManager.addBalance(target, playerShare);
            economyManager.addServerBank(bankShare, "Spendensteuer: " + player.getName() + " -> " + request.targetName());
            player.sendMessage(ChatColor.GREEN + "Spende gesendet: $" + Money.format(playerShare) + " an " + request.targetName() + ", $" + Money.format(bankShare) + " an die Serverkasse.");
            if (target.getPlayer() != null) {
                target.getPlayer().sendMessage(ChatColor.GREEN + player.getName() + " hat dir $" + Money.format(playerShare) + " gespendet.");
                scoreboardManager.apply(target.getPlayer());
            }
        }
        scoreboardManager.apply(player);
        refreshScoreboards();
        return true;
    }

    private boolean serverBank(CommandSender sender, String[] args) {
        if (!commandEnabled(sender, "serverbank")) {
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("in") || args[0].equalsIgnoreCase("out"))) {
            return showServerBankHistory(sender, args);
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /serverbank <set|add|withdraw|remove> <amount> oder /serverbank <in|out> [amount]");
            return true;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Bitte gib eine gültige Zahl an.");
            return true;
        }
        if (!applyServerBankOperation(sender, args[0].toLowerCase(), amount)) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /serverbank <set|add|withdraw|remove> <amount> oder /serverbank <in|out> [amount]");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Serverkasse: $" + Money.format(economyManager.getServerBank()));
        refreshScoreboards();
        return true;
    }

    private boolean showServerBankHistory(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /serverbank <in|out> [amount]");
            return true;
        }
        int amount = plugin.configInt("server-bank.history.default-command-amount", 25);
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(ChatColor.RED + "Bitte gib eine gültige Anzahl an.");
                return true;
            }
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Die Anzahl muss größer als 0 sein.");
                return true;
            }
        }
        String direction = args[0].equalsIgnoreCase("in") ? "in" : "out";
        List<String> entries = economyManager.getServerBankEntries(direction);
        sender.sendMessage(ChatColor.GOLD + "Letzte " + amount + " Serverkassen-" + (direction.equals("in") ? "Einzahlungen" : "Auszahlungen") + ":");
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Einträge vorhanden.");
            return true;
        }
        entries.stream().limit(amount).forEach(entry -> sender.sendMessage(ChatColor.GRAY + entry));
        return true;
    }

    private void sendDonationConfirmation(Player player, DonationRequest request) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        request = request.withToken(token);
        pendingDonations.put(player.getUniqueId(), request);
        BigDecimal bankShare = request.serverBank() ? request.amount() : request.amount().multiply(percent("donations.player-tax-percent", 10.0));
        BigDecimal playerShare = request.serverBank() ? BigDecimal.ZERO : request.amount().subtract(bankShare);
        TextComponent message = new TextComponent(ChatColor.GREEN + "[Spende bestätigen]");
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/donateconfirm " + token));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(
            "Empfänger: " + (request.serverBank() ? "Serverkasse" : request.targetName())
                + "\nAn Spieler: $" + Money.format(playerShare)
                + "\nAn Serverkasse: $" + Money.format(bankShare)
                + "\nGesamt: $" + Money.format(request.amount())
        ).create()));
        player.sendMessage(ChatColor.YELLOW + "Klicke zum Bestätigen:");
        if (plugin.configBool("donations.require-click-confirmation", true)) {
            player.spigot().sendMessage(message);
        } else {
            player.performCommand("donateconfirm " + token);
        }
    }

    private BigDecimal parsePositiveAmount(Player player, String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                player.sendMessage(ChatColor.RED + "Der Betrag muss größer als 0 sein.");
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "Bitte gib einen gültigen Betrag an.");
            return null;
        }
    }

    private boolean applyMoneyOperation(CommandSender sender, OfflinePlayer target, String operation, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        switch (operation) {
            case "set" -> economyManager.setBalance(target, amount);
            case "add" -> economyManager.addBalance(target, amount);
            case "withdraw" -> {
                BigDecimal current = economyManager.getBalanceExact(target.getUniqueId());
                BigDecimal withdrawn = withdrawableAmount(current, amount);
                BigDecimal next = current.subtract(amount);
                if (!plugin.configBool("economy.allow-negative-admin-withdraw", false)) {
                    next = next.max(BigDecimal.ZERO);
                }
                economyManager.setBalance(target, next);
                creditAdmin(sender, withdrawn, "Auszahlung von " + target.getName());
            }
            case "remove" -> {
                BigDecimal next = economyManager.getBalanceExact(target.getUniqueId()).subtract(amount);
                economyManager.setBalance(target, next.max(BigDecimal.ZERO));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean applyServerBankOperation(CommandSender sender, String operation, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        switch (operation) {
            case "set" -> economyManager.setServerBank(amount, "Admin set");
            case "add" -> economyManager.addServerBank(amount, "Admin add");
            case "withdraw" -> {
                BigDecimal current = economyManager.getServerBank();
                BigDecimal withdrawn = withdrawableAmount(current, amount);
                economyManager.setServerBank(current.subtract(amount), "Admin withdraw");
                creditAdmin(sender, withdrawn, "Auszahlung aus der Serverkasse");
            }
            case "remove" -> economyManager.setServerBank(economyManager.getServerBank().subtract(amount), "Admin remove");
            default -> {
                return false;
            }
        }
        return true;
    }

    private BigDecimal withdrawableAmount(BigDecimal current, BigDecimal requested) {
        return current.min(requested).max(BigDecimal.ZERO);
    }

    private void creditAdmin(CommandSender sender, BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (sender instanceof Player admin) {
            economyManager.addBalance(admin, amount);
            admin.sendMessage(ChatColor.GREEN + reason + ": $" + Money.format(amount) + " wurden deinem Konto gutgeschrieben.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + reason + ": $" + Money.format(amount) + " wurden entfernt. Die Konsole kann kein Geld erhalten.");
        }
    }

    private boolean commandEnabled(CommandSender sender, String name) {
        if (plugin.configBool("commands." + name + "-enabled", true)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Dieser Befehl ist deaktiviert.");
        return false;
    }

    private BigDecimal percent(String path, double fallback) {
        return BigDecimal.valueOf(Math.max(0.0, Math.min(100.0, plugin.configDouble(path, fallback))) / 100.0);
    }

    private void refreshScoreboards() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            scoreboardManager.apply(online);
        }
    }

    private boolean hasAdmin(CommandSender sender) {
        if (sender.hasPermission("serverutilities.admin")) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Dafür hast du keine Rechte.");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("market")) {
            if (args.length == 1) {
                for (String option : List.of("buy", "sell", "search")) {
                    if (option.startsWith(args[0].toLowerCase())) {
                        suggestions.add(option);
                    }
                }
                return suggestions;
            }
            if (args.length == 2 && List.of("buy", "sell").contains(args[0].toLowerCase())) {
                String prefix = args[1].toUpperCase();
                for (MarketItem item : marketManager.allItems()) {
                    Material material = item.material();
                    if (material.name().startsWith(prefix)) {
                        suggestions.add(material.name().toLowerCase());
                        if (suggestions.size() >= 50) {
                            break;
                        }
                    }
                }
                return suggestions;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
                return marketItemSuggestions(args[1]);
            }
            if (args.length == 3 && List.of("buy", "sell").contains(args[0].toLowerCase())) {
                return List.of("1", "8", "32", "64");
            }
            return suggestions;
        }
        if (command.getName().equalsIgnoreCase("donate")) {
            return donateSuggestions(args);
        }
        if (!sender.hasPermission("serverutilities.admin")) {
            return suggestions;
        }
        if (args.length == 1 && List.of("balance", "checkbalance", "checkhistory", "tode", "checktode").contains(command.getName().toLowerCase())) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(player.getName());
                }
            }
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("balance")) {
            return List.of("set", "add", "withdraw", "remove");
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("serverbank")) {
            return List.of("set", "add", "withdraw", "remove", "in", "out");
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("serverbank") && List.of("in", "out").contains(args[0].toLowerCase())) {
            return List.of("10", "25", "50", "100");
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("checkhistory")) {
            return List.of("10", "25", "50", "100");
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("resetmarket")) {
            String prefix = args[0].toUpperCase();
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isAir() && material.name().startsWith(prefix)) {
                    suggestions.add(material.name().toLowerCase());
                    if (suggestions.size() >= 50) {
                        break;
                    }
                }
            }
        }
        return suggestions;
    }

    private List<String> donateSuggestions(String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (String option : List.of("serverkasse", "player")) {
                if (option.startsWith(args[0].toLowerCase())) {
                    suggestions.add(option);
                }
            }
            suggestions.addAll(onlinePlayerSuggestions(args[0]));
            return suggestions;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("player")) {
                return onlinePlayerSuggestions(args[1]);
            }
            return amountSuggestions(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("player")) {
            return amountSuggestions(args[2]);
        }
        return List.of();
    }

    private List<String> onlinePlayerSuggestions(String rawPrefix) {
        List<String> suggestions = new ArrayList<>();
        String prefix = rawPrefix.toLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(prefix)) {
                suggestions.add(player.getName());
            }
        }
        return suggestions;
    }

    private List<String> amountSuggestions(String rawPrefix) {
        List<String> suggestions = new ArrayList<>();
        for (String amount : List.of("10", "100", "1000", "10000")) {
            if (amount.startsWith(rawPrefix)) {
                suggestions.add(amount);
            }
        }
        return suggestions;
    }

    private List<String> marketItemSuggestions(String rawPrefix) {
        List<String> suggestions = new ArrayList<>();
        String prefix = rawPrefix.toUpperCase();
        for (MarketItem item : marketManager.allItems()) {
            Material material = item.material();
            if (material.name().startsWith(prefix)) {
                suggestions.add(material.name().toLowerCase());
                if (suggestions.size() >= 50) {
                    break;
                }
            }
        }
        return suggestions;
    }

    private record DonationRequest(UUID target, String targetName, BigDecimal amount, boolean serverBank, String token) {
        private static DonationRequest player(UUID target, String targetName, BigDecimal amount) {
            return new DonationRequest(target, targetName, amount, false, "");
        }

        private static DonationRequest serverBank(BigDecimal amount) {
            return new DonationRequest(null, "Serverkasse", amount, true, "");
        }

        private DonationRequest withToken(String token) {
            return new DonationRequest(target, targetName, amount, serverBank, token);
        }
    }
}
