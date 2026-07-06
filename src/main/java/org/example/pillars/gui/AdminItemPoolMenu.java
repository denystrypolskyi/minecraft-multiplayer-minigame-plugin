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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AdminItemPoolMenu implements InventoryHolder {
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("pillars", "admin_pool_action");
    private static final NamespacedKey MATERIAL_KEY = new NamespacedKey("pillars", "admin_pool_material");
    private static final int MENU_SIZE = 54;

    private final Inventory inventory;
    private final Player player;
    private final ItemManager itemManager;
    private final HudManager hudManager;
    private final String rarity;

    public AdminItemPoolMenu(Player player, ItemManager itemManager, HudManager hudManager, String rarity) {
        this.player = player;
        this.itemManager = itemManager;
        this.hudManager = hudManager;
        this.rarity = rarity.toLowerCase();
        this.inventory = Bukkit.createInventory(this, MENU_SIZE, "§4Items: §f" + this.rarity);
        buildMenu();
    }

    private void buildMenu() {
        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, filler());
        }

        inventory.setItem(0, actionItem(Material.ARROW, "§eBack", List.of("§7Return to admin menu."), "back"));
        inventory.setItem(4, infoItem());
        inventory.setItem(8, actionItem(Material.LIME_DYE, "§aAdd Held Item", List.of(
                "§7Adds your main-hand item",
                "§7using the default weight for this rarity."
        ), "add_held"));

        int slot = 9;
        for (Map.Entry<Material, Integer> entry : itemManager.getItemPool(rarity).entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .toList()) {
            if (slot >= MENU_SIZE) {
                break;
            }

            inventory.setItem(slot++, poolItem(entry.getKey(), entry.getValue()));
        }
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
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
            meta.setDisplayName("§6§l" + capitalize(rarity) + " Pool");
            meta.setLore(List.of(
                    "§7Click an item to disable it.",
                    "§7Use Add Held Item to add or re-enable."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack poolItem(Material material, int weight) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + material.name());
            meta.setLore(List.of(
                    "§7Weight: §f" + weight,
                    "§cClick to disable"
            ));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "remove");
            meta.getPersistentDataContainer().set(MATERIAL_KEY, PersistentDataType.STRING, material.name());
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

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        String action = meta.getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "back" -> new AdminHubMenu(clicker, itemManager, hudManager).open();
            case "add_held" -> addHeldItem(clicker);
            case "remove" -> removeItem(clicker, meta);
            default -> {
            }
        }
    }

    private void addHeldItem(Player clicker) {
        ItemStack heldItem = clicker.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            hudManager.sendHoldItemToConfigure(clicker);
            return;
        }

        if (itemManager.addItemWithDefaultWeight(rarity, heldItem.getType())) {
            hudManager.sendItemConfigured(clicker, heldItem.getType(), rarity, itemManager.getDefaultWeight(rarity));
            buildMenu();
        }
    }

    private void removeItem(Player clicker, ItemMeta meta) {
        String materialName = meta.getPersistentDataContainer().get(MATERIAL_KEY, PersistentDataType.STRING);
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null) {
            hudManager.sendUnknownMaterial(clicker);
            return;
        }

        if (itemManager.removeItem(rarity, material)) {
            hudManager.sendItemRemoved(clicker, material, rarity);
            buildMenu();
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isAdminItemPoolMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof AdminItemPoolMenu;
    }
}
