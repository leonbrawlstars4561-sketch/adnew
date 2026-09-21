package de.ahshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Eine Doppelkiste (54 Slots), komplett gefuellt mit dem gewaehlten Item.
 */
public final class ShopMenu implements InventoryHolder {

    public enum Variant { SINGLE, STACK, SHULKER }

    private static final int SIZE = 54;
    private static final int SHULKER_SLOTS = 27;
    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    private final Material material;
    private final Variant variant;
    private final int stackAmount;
    private final double price;
    private final Inventory inventory;

    public ShopMenu(Material material, Variant variant, double unitPrice) {
        this.material = material;
        this.variant = variant;
        // "Stack" = so viel wie in einen Slot passt (64, bei Enderperlen 16, bei Schwertern 1)
        this.stackAmount = Math.max(1, material.getMaxStackSize());
        this.price = switch (variant) {
            case SINGLE -> unitPrice;
            case STACK -> unitPrice * stackAmount;
            case SHULKER -> unitPrice * stackAmount * SHULKER_SLOTS;
        };

        Component title = Component.translatable(material);
        if (variant == Variant.STACK) {
            title = title.append(Component.text(" (Stack)"));
        } else if (variant == Variant.SHULKER) {
            title = title.append(Component.text(" (Shulker)"));
        }
        this.inventory = Bukkit.createInventory(this, SIZE, title);

        ItemStack display = createDisplayItem();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, display.clone());
        }
    }

    public double getPrice() {
        return price;
    }

    /** Anzeigename fuer die Kauf-Bestaetigung im Chat. */
    public Component getPurchaseName() {
        if (variant == Variant.SHULKER) {
            return Component.translatable(Material.SHULKER_BOX)
                    .append(Component.text(" ("))
                    .append(Component.translatable(material))
                    .append(Component.text(")"));
        }
        return Component.translatable(material);
    }

    public static String formatMoney(double amount) {
        return MONEY.format(amount);
    }

    /** Das Item, das der Spieler wirklich bekommt. */
    public ItemStack createPurchaseItem() {
        return switch (variant) {
            case SINGLE -> new ItemStack(material, 1);
            case STACK -> new ItemStack(material, stackAmount);
            case SHULKER -> {
                ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
                BlockStateMeta meta = (BlockStateMeta) shulker.getItemMeta();
                ShulkerBox box = (ShulkerBox) meta.getBlockState();
                for (int i = 0; i < SHULKER_SLOTS; i++) {
                    box.getInventory().setItem(i, new ItemStack(material, stackAmount));
                }
                meta.setBlockState(box);
                shulker.setItemMeta(meta);
                yield shulker;
            }
        };
    }

    /** Das Item, das in der GUI angezeigt wird: nur Name + gruener Preis. */
    private ItemStack createDisplayItem() {
        ItemStack item = switch (variant) {
            case SINGLE -> new ItemStack(material, 1);
            case STACK -> new ItemStack(material, stackAmount);
            case SHULKER -> new ItemStack(Material.SHULKER_BOX);
        };
        item.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.values()); // alle Zusatz-Tooltips ausblenden
            if (variant == Variant.SHULKER) {
                meta.itemName(Component.translatable(Material.SHULKER_BOX)
                        .append(Component.text(" ("))
                        .append(Component.translatable(material))
                        .append(Component.text(")")));
            }
            meta.lore(List.of(
                    Component.text("$", NamedTextColor.GREEN)
                            .append(Component.text(" " + formatMoney(price), NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false)));
        });
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
