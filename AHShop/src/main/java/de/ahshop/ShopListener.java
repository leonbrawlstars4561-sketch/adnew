package de.ahshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopListener implements Listener {

    private final AHShop plugin;

    public ShopListener(AHShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopMenu menu)) {
            return;
        }
        // In der Shop-GUI darf nie etwas verschoben werden
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // Klick ins eigene Inventar
        }
        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT
                && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            return;
        }
        if (event.getCurrentItem() == null) {
            return;
        }
        plugin.buy(player, menu);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu) {
            event.setCancelled(true);
        }
    }
}
