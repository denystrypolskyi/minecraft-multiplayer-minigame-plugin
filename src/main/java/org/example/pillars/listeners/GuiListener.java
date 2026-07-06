package org.example.pillars.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.example.pillars.gui.AdminConfigMenu;
import org.example.pillars.gui.ArenaMenu;

public class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == null) return;

        if (ArenaMenu.isArenaMenu(event.getClickedInventory())) {
            ArenaMenu menu = (ArenaMenu) event.getClickedInventory().getHolder();
            menu.handleClick(event);
        } else if (AdminConfigMenu.isAdminConfigMenu(event.getClickedInventory())) {
            AdminConfigMenu menu = (AdminConfigMenu) event.getClickedInventory().getHolder();
            menu.handleClick(event);
        }
    }
}
