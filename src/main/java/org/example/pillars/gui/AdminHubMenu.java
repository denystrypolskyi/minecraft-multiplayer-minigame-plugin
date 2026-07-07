package org.example.pillars.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.example.pillars.managers.ArenaManager;
import org.example.pillars.managers.GameSessionManager;
import org.example.pillars.managers.HudManager;
import org.example.pillars.managers.ItemManager;

import java.util.List;

public class AdminHubMenu implements InventoryHolder {
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("pillars", "admin_hub_action");
    private static final int MENU_SIZE = 27;
    private static final String MENU_TITLE = "§4Pillars Admin";

    private final Inventory inventory;
    private final Player player;
    private final ItemManager itemManager;
    private final HudManager hudManager;
    private final ArenaManager arenaManager;
    private final GameSessionManager gameSessionManager;

    public AdminHubMenu(Player player, ItemManager itemManager, HudManager hudManager, ArenaManager arenaManager, GameSessionManager gameSessionManager) {
        this.player = player;
        this.itemManager = itemManager;
        this.hudManager = hudManager;
        this.arenaManager = arenaManager;
        this.gameSessionManager = gameSessionManager;
        this.inventory = Bukkit.createInventory(this, MENU_SIZE, MENU_TITLE);
        buildMenu();
    }

    private void buildMenu() {
        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, filler());
        }

        inventory.setItem(4, actionItem(
                Material.MAP,
                "§eArena Settings",
                List.of("§7Edit safe numeric arena settings."),
                "arenas"
        ));
        inventory.setItem(10, actionItem(
                Material.COMPARATOR,
                "§6Rarity Chances",
                List.of("§7Adjust common, rare, and legendary drop rates."),
                "rarity"
        ));
        inventory.setItem(12, actionItem(
                Material.CHEST,
                "§aCommon Items",
                List.of("§7Add or remove common pool items."),
                "pool:common"
        ));
        inventory.setItem(14, actionItem(
                Material.ENDER_CHEST,
                "§bRare Items",
                List.of("§7Add or remove rare pool items."),
                "pool:rare"
        ));
        inventory.setItem(16, actionItem(
                Material.NETHERITE_BLOCK,
                "§6Legendary Items",
                List.of("§7Add or remove legendary pool items."),
                "pool:legendary"
        ));
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.hasPermission("pillars.admin")) {
            hudManager.sendNoPermission(clicker);
            clicker.closeInventory();
            return;
        }

        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        String action = event.getCurrentItem().getItemMeta()
                .getPersistentDataContainer()
                .get(ACTION_KEY, PersistentDataType.STRING);

        if (action == null) return;

        if (action.equals("rarity")) {
            new AdminConfigMenu(clicker, itemManager, hudManager, arenaManager, gameSessionManager).open();
            return;
        }

        if (action.equals("arenas")) {
            new AdminArenaListMenu(clicker, arenaManager, gameSessionManager, itemManager, hudManager).open();
            return;
        }

        if (action.startsWith("pool:")) {
            new AdminItemPoolMenu(clicker, arenaManager, gameSessionManager, itemManager, hudManager, action.substring("pool:".length())).open();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isAdminHubMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof AdminHubMenu;
    }
}
