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
import org.example.pillars.enums.GameState;
import org.example.pillars.managers.ArenaManager;
import org.example.pillars.managers.GameSessionManager;
import org.example.pillars.managers.HudManager;

import java.util.ArrayList;
import java.util.List;

public class ArenaMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Player player;
    private final ArenaManager arenaManager;
    private final GameSessionManager sessionManager;
    private final HudManager hudManager;

    private static final NamespacedKey ARENA_KEY = new NamespacedKey("pillars", "arena_worldname");
    private static final int MENU_SIZE = 45;
    private static final String MENU_TITLE = "§5Arena Selection";
    private static final String MENU_SEPARATOR = "§8»»»»»»»»»»»»»»»";
    private static final String STATUS_LABEL = "§fStatus:  ";
    private static final String PLAYERS_LABEL = "§fPlayers:  ";
    private static final String JOIN_LORE = "§a✦ Click to join ✦";
    private static final String STARTING_LORE = "§e⏳ Starting soon...";
    private static final String RUNNING_LORE = "§c⚔ Match in progress";
    private static final String UNAVAILABLE_LORE = "§7⌛ Unavailable";

    public ArenaMenu(Player player, ArenaManager arenaManager, GameSessionManager sessionManager, HudManager hudManager) {
        this.player = player;
        this.arenaManager = arenaManager;
        this.sessionManager = sessionManager;
        this.hudManager = hudManager;

        this.inventory = Bukkit.createInventory(this, MENU_SIZE, MENU_TITLE);
        buildMenu();
    }

    private void buildMenu() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        List<Arena> arenas = arenaManager.getArenas().stream()
                .sorted((a1, a2) -> {
                    int size1 = a1.getSpawnPoints().size();
                    int size2 = a2.getSpawnPoints().size();
                    if (size1 != size2) return Integer.compare(size1, size2);
                    return a1.getDisplayName().compareTo(a2.getDisplayName());
                })
                .toList();

        int row4Players = 9;
        int row8Players = 18;
        int row12Players = 27;

        List<Arena> arenas4 = new ArrayList<>();
        List<Arena> arenas8 = new ArrayList<>();
        List<Arena> arenas12 = new ArrayList<>();

        for (Arena arena : arenas) {
            int spawnCount = arena.getSpawnPoints().size();
            if (spawnCount == 4) arenas4.add(arena);
            else if (spawnCount == 8) arenas8.add(arena);
            else if (spawnCount == 12) arenas12.add(arena);
        }

        placeArenaGroup(arenas4, row4Players);
        placeArenaGroup(arenas8, row8Players);
        placeArenaGroup(arenas12, row12Players);
    }

    private void placeArenaGroup(List<Arena> group, int rowStartSlot) {
        if (group.isEmpty()) return;

        int groupSize = group.size();
        int startSlot = rowStartSlot + (9 - groupSize) / 2;

        for (Arena arena : group) {
            GameState state = GameState.WAITING;
            int currentPlayers = 0;
            int maxPlayers = arena.getSpawnPoints().size();
            String statusColor = "§a";
            String playerIcon = "§a👥";

            if (sessionManager.getSession(arena) != null) {
                state = sessionManager.getSession(arena).getState();
                currentPlayers = sessionManager.getSession(arena).getAllPlayerIds().size();

                statusColor = switch (state) {
                    case WAITING -> "§a";
                    case STARTING -> "§e";
                    case RUNNING -> "§c";
                    case ENDING, RESETTING -> "§7";
                    default -> "§f";
                };

                playerIcon = currentPlayers >= maxPlayers ? "§c🔥" : "§a👥";
            }

            ItemStack item = new ItemStack(getMaterialForState(state));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§l§f" + arena.getDisplayName());

                List<String> lore = new ArrayList<>();
                lore.add(MENU_SEPARATOR);
                lore.add(STATUS_LABEL + statusColor + getStateDisplay(state));
                lore.add(PLAYERS_LABEL + "§e" + currentPlayers + "§7/§f" + maxPlayers + " " + playerIcon);
                lore.add(MENU_SEPARATOR);

                if (state == GameState.WAITING) lore.add(JOIN_LORE);
                else if (state == GameState.STARTING) lore.add(STARTING_LORE);
                else if (state == GameState.RUNNING) lore.add(RUNNING_LORE);
                else lore.add(UNAVAILABLE_LORE);

                meta.setLore(lore);
                meta.getPersistentDataContainer().set(ARENA_KEY, PersistentDataType.STRING, arena.getWorldName());
                item.setItemMeta(meta);
            }

            inventory.setItem(startSlot, item);
            startSlot++;
        }
    }

    private String getStateDisplay(GameState state) {
        return switch (state) {
            case WAITING -> "Waiting for players";
            case STARTING -> "Starting";
            case RUNNING -> "In game";
            case ENDING -> "Ending";
            case RESETTING -> "Resetting arena";
            default -> state.name();
        };
    }

    private Material getMaterialForState(GameState state) {
        return switch (state) {
            case WAITING -> Material.LIME_CONCRETE;
            case STARTING -> Material.YELLOW_CONCRETE;
            case RUNNING -> Material.RED_CONCRETE;
            case ENDING, RESETTING -> Material.GRAY_CONCRETE;
            default -> Material.WHITE_CONCRETE;
        };
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        String worldName = event.getCurrentItem().getItemMeta()
                .getPersistentDataContainer().get(ARENA_KEY, PersistentDataType.STRING);

        if (worldName == null) return;

        Arena arena = arenaManager.getArena(worldName);
        if (arena == null) return;

        if (sessionManager.getSession(arena) != null &&
                sessionManager.getSession(arena).getState() != GameState.WAITING) {
            hudManager.sendGameAlreadyRunning(player);
            return;
        }

        sessionManager.joinSession(player, arena);
        player.closeInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static boolean isArenaMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof ArenaMenu;
    }
}
