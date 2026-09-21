package de.ahshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AHShop extends JavaPlugin implements TabExecutor {

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault wurde nicht gefunden - Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        File file = new File(getDataFolder(), "prices.txt");
        if (!file.exists()) {
            saveResource("prices.txt", false);
        }
        loadPrices();

        PluginCommand command = getCommand("ah");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
    }

    /** Vault-Economy wird bei jedem Kauf frisch geholt (Economy-Plugin kann spaeter laden). */
    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    // ------------------------------------------------------------------ Preise

    private void loadPrices() {
        prices.clear();

        File file = new File(getDataFolder(), "prices.txt");
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().severe("prices.txt konnte nicht gelesen werden: " + e.getMessage());
            return;
        }

        List<String> unknown = new ArrayList<>();
        List<String> noPrice = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx < 0) {
                continue;
            }
            String key = line.substring(0, idx).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            String value = line.substring(idx + 1).trim();
            if (key.isEmpty()) {
                continue;
            }
            if (value.isEmpty()) {
                noPrice.add(key);
                continue;
            }
            double price;
            try {
                price = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                noPrice.add(key);
                continue;
            }

            Material material = Material.matchMaterial(key);
            if (material == null && key.startsWith("BLOCK_OF_")) {
                // z.B. BLOCK_OF_RAW_IRON -> RAW_IRON_BLOCK
                material = Material.matchMaterial(key.substring("BLOCK_OF_".length()) + "_BLOCK");
            }
            if (material == null) {
                unknown.add(key);
                continue;
            }
            if (!material.isItem() || material.isAir()) {
                continue;
            }
            prices.put(material, price); // doppelte Eintraege: der letzte gewinnt
        }

        getLogger().info(prices.size() + " Preise geladen.");
        if (!noPrice.isEmpty()) {
            getLogger().warning("Eintraege ohne gueltigen Preis (uebersprungen): " + noPrice);
        }
        if (!unknown.isEmpty()) {
            getLogger().warning("Unbekannte Materialien (uebersprungen): " + unknown);
        }
    }

    // ------------------------------------------------------------------ Command

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")
                && sender.hasPermission("ahshop.reload")) {
            loadPrices();
            sender.sendMessage(Component.text("Preisliste neu geladen (" + prices.size() + " Items).",
                    NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen /ah benutzen.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Benutzung: /ah <block> [stack|shulker]", NamedTextColor.RED));
            return true;
        }

        ShopMenu.Variant variant = ShopMenu.Variant.SINGLE;
        int end = args.length;
        if (args.length >= 2) {
            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (last.equals("stack")) {
                variant = ShopMenu.Variant.STACK;
                end--;
            } else if (last.equals("shulker")) {
                variant = ShopMenu.Variant.SHULKER;
                end--;
            }
        }

        String name = String.join("_", Arrays.copyOfRange(args, 0, end)).toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(name);
        Double unitPrice = material == null ? null : prices.get(material);
        if (material == null || unitPrice == null) {
            player.sendMessage(Component.text("Unbekannter Block oder kein Preis hinterlegt: "
                    + name.toLowerCase(Locale.ROOT), NamedTextColor.RED));
            return true;
        }
        if (variant == ShopMenu.Variant.SHULKER && material.name().endsWith("SHULKER_BOX")) {
            player.sendMessage(Component.text("Shulkerboxen passen nicht in eine Shulkerbox.",
                    NamedTextColor.RED));
            return true;
        }

        player.openInventory(new ShopMenu(material, variant, unitPrice).getInventory());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            if ("reload".startsWith(prefix) && sender.hasPermission("ahshop.reload")) {
                result.add("reload");
            }
            result.add("<search>");
        } else {
            for (String s : List.of("stack", "shulker")) {
                if (s.startsWith(prefix)) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ Kauf

    /** Ein Klick = Kauf. Keine Bestaetigung, unendlich oft moeglich. */
    public void buy(Player player, ShopMenu menu) {
        Economy economy = economy();
        if (economy == null) {
            player.sendActionBar(Component.text("Kein Economy-Plugin gefunden.", NamedTextColor.RED));
            return;
        }

        double price = menu.getPrice();
        ItemStack item = menu.createPurchaseItem();

        if (!canFit(player, item)) {
            player.sendActionBar(Component.text("Dein Inventar ist voll.", NamedTextColor.RED));
            return;
        }
        if (!economy.has(player, price)) {
            player.sendActionBar(Component.text("Nicht genug Geld! Du brauchst $ "
                    + ShopMenu.formatMoney(price), NamedTextColor.RED));
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (!response.transactionSuccess()) {
            player.sendActionBar(Component.text("Kauf fehlgeschlagen.", NamedTextColor.RED));
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        // Der einzige Sound im ganzen Shop
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);

        Component message = Component.text("You bought ", NamedTextColor.WHITE)
                .append(Component.text(item.getAmount() + " ", NamedTextColor.WHITE))
                .append(menu.getPurchaseName().color(NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.WHITE))
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(" " + ShopMenu.formatMoney(price), NamedTextColor.WHITE));
        player.sendMessage(message);
    }

    private static boolean canFit(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int max = item.getMaxStackSize();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= max;
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, max - slot.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }
}
