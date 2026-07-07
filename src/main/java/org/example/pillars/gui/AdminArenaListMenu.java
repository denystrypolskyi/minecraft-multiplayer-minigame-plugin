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
import org.example.pillars.entities.Arena;
import org.example.pillars.managers.ArenaManager;
import org.example.pillars.managers.GameSessionManager;
import org.example.pillars.managers.HudManager;
import org.example.pillars.managers.ItemManager;

import java.util.List;

public class AdminArenaListMenu implements InventoryHolder {
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("pillars", "admin_arena_action");
    private static final NamespacedKey ARENA_KEY = new NamespacedKey("pillars", "admin_arena_world");
    private static final int MENU_SIZE = 54;
    private static final String MENU_TITLE = "§4Arena Settings";

    private final Inventory inventory;
    private final Player player;
    private final ArenaManager arenaManager;
    private final GameSessionManager gameSessionManager;
    private final ItemManager itemManager;
    private final HudManager hudManager;

    public AdminArenaListMenu(Player player, ArenaManager arenaManager, GameSessionManager gameSessionManager, ItemManager itemManager, HudManager hudManager) {
        this.player = player;
        this.arenaManager = arenaManager;
        this.gameSessionManager = gameSessionManager;
        this.itemManager = itemManager;
        this.hudManager = hudManager;
        this.inventory = Bukkit.createInventory(this, MENU_SIZE, MENU_TITLE);
        buildMenu();
    }

    private void buildMenu() {
        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, filler());
        }

        inventory.setItem(0, actionItem(Material.ARROW, "§eBack", List.of("§7Return to admin menu."), "back"));

        int slot = 9;
        for (Arena arena : arenaManager.getArenas().stream()
                .sorted(ArenaMenuItemFactory.arenaListOrder())
                .toList()) {
            if (slot >= MENU_SIZE) {
                break;
            }
            inventory.setItem(slot++, ArenaMenuItemFactory.adminArenaItem(
                    arena,
                    gameSessionManager.getSession(arena),
                    ACTION_KEY,
                    ARENA_KEY
            ));
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

        if (action.equals("back")) {
            new AdminHubMenu(clicker, itemManager, hudManager, arenaManager, gameSessionManager).open();
            return;
        }

        if (action.equals("edit")) {
            String worldName = meta.getPersistentDataContainer().get(ARENA_KEY, PersistentDataType.STRING);
            Arena arena = arenaManager.getArena(worldName);
            if (arena != null) {
                new AdminArenaSettingsMenu(clicker, arenaManager, gameSessionManager, itemManager, hudManager, arena).open();
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isAdminArenaListMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof AdminArenaListMenu;
    }
}
