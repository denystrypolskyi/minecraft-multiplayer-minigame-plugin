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
import org.example.pillars.managers.HudManager;
import org.example.pillars.managers.ItemManager;

import java.util.List;

public class AdminConfigMenu implements InventoryHolder {
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("pillars", "admin_action");
    private static final int MENU_SIZE = 27;
    private static final String MENU_TITLE = "§4Pillars Admin";

    private final Inventory inventory;
    private final Player player;
    private final ItemManager itemManager;
    private final HudManager hudManager;

    public AdminConfigMenu(Player player, ItemManager itemManager, HudManager hudManager) {
        this.player = player;
        this.itemManager = itemManager;
        this.hudManager = hudManager;
        this.inventory = Bukkit.createInventory(this, MENU_SIZE, MENU_TITLE);
        buildMenu();
    }

    private void buildMenu() {
        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, filler());
        }

        inventory.setItem(4, infoItem());

        inventory.setItem(10, actionItem(Material.RED_DYE, "§cLegendary -5%", "legendary:-5"));
        inventory.setItem(11, actionItem(Material.REDSTONE, "§cLegendary -1%", "legendary:-1"));
        inventory.setItem(12, displayItem(Material.NETHERITE_BLOCK, "§6Legendary", itemManager.getLegendaryPercent()));
        inventory.setItem(13, actionItem(Material.GLOWSTONE_DUST, "§aLegendary +1%", "legendary:1"));
        inventory.setItem(14, actionItem(Material.LIME_DYE, "§aLegendary +5%", "legendary:5"));

        inventory.setItem(16, actionItem(Material.ORANGE_DYE, "§cRare -5%", "rare:-5"));
        inventory.setItem(17, actionItem(Material.COPPER_INGOT, "§cRare -1%", "rare:-1"));
        inventory.setItem(18, displayItem(Material.OBSIDIAN, "§bRare", itemManager.getRarePercent()));
        inventory.setItem(19, actionItem(Material.LAPIS_LAZULI, "§aRare +1%", "rare:1"));
        inventory.setItem(20, actionItem(Material.EMERALD, "§aRare +5%", "rare:5"));

        inventory.setItem(22, displayItem(Material.STONE, "§7Common", itemManager.getCommonPercent()));
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

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lItem Rarity");
            meta.setLore(List.of(
                    "§7Changes save immediately.",
                    "§7Common is calculated automatically."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack displayItem(Material material, String name, int percent) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name + " §f" + percent + "%");
            meta.setLore(List.of("§7Current chance"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack actionItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of("§7Click to update rarity."));
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

        String[] parts = action.split(":");
        if (parts.length != 2) return;

        int delta;
        try {
            delta = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return;
        }

        int legendary = itemManager.getLegendaryPercent();
        int rare = itemManager.getRarePercent();

        if (parts[0].equals("legendary")) {
            legendary += delta;
        } else if (parts[0].equals("rare")) {
            rare += delta;
        }

        itemManager.setRarityPercentages(legendary, rare);
        buildMenu();
        hudManager.sendAdminConfigUpdated(clicker, itemManager.getCommonPercent(), itemManager.getRarePercent(), itemManager.getLegendaryPercent());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isAdminConfigMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof AdminConfigMenu;
    }
}
