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

public class AdminArenaSettingsMenu implements InventoryHolder {
    private static final NamespacedKey ACTION_KEY = new NamespacedKey("pillars", "admin_arena_setting_action");
    private static final int MENU_SIZE = 27;

    private final Inventory inventory;
    private final Player player;
    private final ArenaManager arenaManager;
    private final GameSessionManager gameSessionManager;
    private final ItemManager itemManager;
    private final HudManager hudManager;
    private final Arena arena;

    public AdminArenaSettingsMenu(Player player, ArenaManager arenaManager, GameSessionManager gameSessionManager, ItemManager itemManager, HudManager hudManager, Arena arena) {
        this.player = player;
        this.arenaManager = arenaManager;
        this.gameSessionManager = gameSessionManager;
        this.itemManager = itemManager;
        this.hudManager = hudManager;
        this.arena = arena;
        this.inventory = Bukkit.createInventory(this, MENU_SIZE, "§4Arena: §f" + arena.getDisplayName());
        buildMenu();
    }

    private void buildMenu() {
        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, filler());
        }

        inventory.setItem(0, actionItem(Material.ARROW, "§eBack", List.of("§7Return to arena list."), "back"));
        inventory.setItem(4, infoItem());
        inventory.setItem(8, actionItem(
                arena.isJoiningOpen() ? Material.OAK_DOOR : Material.IRON_DOOR,
                arena.isJoiningOpen() ? "§cClose Joining" : "§aOpen Joining",
                List.of("§7Runtime testing control.", "§7Existing players stay in the arena."),
                "toggle_joining"
        ));

        inventory.setItem(9, actionItem(Material.RED_DYE, "§cMin Players -1", List.of("§7Minimum is 1."), "min:-1"));
        inventory.setItem(10, displayItem(Material.PLAYER_HEAD, "§bMin Players", arena.getMinPlayers() + "/" + arena.getSpawnPoints().size()));
        inventory.setItem(11, actionItem(Material.LIME_DYE, "§aMin Players +1", List.of("§7Maximum is arena capacity."), "min:1"));

        inventory.setItem(15, actionItem(Material.REDSTONE, "§cCooldown -1s", List.of("§7Minimum is 1 second."), "cooldown:-1"));
        inventory.setItem(16, displayItem(Material.CLOCK, "§eItem Cooldown", arena.getItemCooldownSeconds() + "s"));
        inventory.setItem(17, actionItem(Material.GLOWSTONE_DUST, "§aCooldown +1s", List.of("§7Increases item delivery interval."), "cooldown:1"));

        inventory.setItem(22, actionItem(
                Material.SPYGLASS,
                "§bSpectate Arena",
                List.of("§7Only works while the game is running.", "§7Does not add you as a player."),
                "spectate"
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

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lSafe Arena Settings");
            meta.setLore(List.of(
                    "§7Changes save to config.yml immediately.",
                    "§7Only safe numeric settings are editable here."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack displayItem(Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name + " §f" + value);
            meta.setLore(List.of("§7Current value"));
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

        if (action.equals("back")) {
            new AdminArenaListMenu(clicker, arenaManager, gameSessionManager, itemManager, hudManager).open();
            return;
        }

        if (action.equals("toggle_joining")) {
            gameSessionManager.setArenaJoiningOpen(arena, !arena.isJoiningOpen());
            hudManager.broadcastArenaJoiningChanged(clicker, arena);
            buildMenu();
            return;
        }

        if (action.equals("spectate")) {
            gameSessionManager.spectateSession(clicker, arena);
            return;
        }

        String[] parts = action.split(":");
        if (parts.length != 2) return;

        int delta;
        try {
            delta = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return;
        }

        int minPlayers = arena.getMinPlayers();
        int cooldown = arena.getItemCooldownSeconds();

        if (parts[0].equals("min")) {
            minPlayers += delta;
        } else if (parts[0].equals("cooldown")) {
            cooldown += delta;
        }

        arenaManager.updateSafeArenaSettings(arena, minPlayers, cooldown);
        hudManager.sendArenaSettingsUpdated(clicker, arena);
        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isAdminArenaSettingsMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof AdminArenaSettingsMenu;
    }
}
