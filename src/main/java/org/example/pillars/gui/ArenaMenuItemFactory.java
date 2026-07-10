package org.example.pillars.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.example.pillars.GameSession;
import org.example.pillars.entities.Arena;
import org.example.pillars.enums.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ArenaMenuItemFactory {
    private ArenaMenuItemFactory() {
    }

    static Comparator<Arena> arenaListOrder() {
        return Comparator
                .comparingInt((Arena arena) -> arena.getSpawnPoints().size())
                .thenComparing(Arena::getDisplayName);
    }

    static ItemStack playerArenaItem(Arena arena, GameSession session, NamespacedKey arenaKey) {
        ArenaView view = ArenaView.from(arena, session);
        ItemStack item = new ItemStack(view.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§l§f" + arena.getDisplayName());
            meta.setLore(List.of(
                    "§7Status: " + view.stateColor() + view.stateDisplay(),
                    "§7Joining: " + view.joiningDisplay(),
                    "§7Players: §f" + view.currentPlayers() + "§7/§f" + view.maxPlayers(),
                    "§7Starts at: §f" + arena.getMinPlayers() + "§7 players",
                    "§7Size: §f" + view.maxPlayers() + " players",
                    view.playerActionLore()
            ));
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getWorldName());
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack adminArenaItem(Arena arena, GameSession session, NamespacedKey actionKey, NamespacedKey arenaKey) {
        ArenaView view = ArenaView.from(arena, session);
        ItemStack item = new ItemStack(view.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(List.of(
                    "§7Status: " + view.stateColor() + view.stateDisplay(),
                    "§7Joining: " + view.joiningDisplay(),
                    "§7Players: §f" + view.currentPlayers() + "§7/§f" + view.maxPlayers(),
                    "§7Starts at: §f" + arena.getMinPlayers() + "§7/§f" + view.maxPlayers() + " players",
                    "§7Item cooldown: §f" + arena.getItemCooldownSeconds() + "s"
            ));
            if (!arena.isJoiningOpen()) {
                lore.add("§8Admin lock is enabled.");
            }
            lore.add("§aClick to control arena settings");

            meta.setDisplayName("§l§f" + arena.getDisplayName());
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "edit");
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getWorldName());
            item.setItemMeta(meta);
        }
        return item;
    }

    private record ArenaView(
            GameState state,
            int currentPlayers,
            int maxPlayers,
            boolean full,
            boolean joinable
    ) {
        static ArenaView from(Arena arena, GameSession session) {
            GameState state = session == null ? GameState.WAITING : session.getState();
            int currentPlayers = session == null ? 0 : session.getActivePlayerIds().size();
            int maxPlayers = arena.getSpawnPoints().size();
            boolean full = currentPlayers >= maxPlayers;
            boolean joinable = arena.isJoiningOpen()
                    && !full
                    && (state == GameState.WAITING || state == GameState.STARTING);

            return new ArenaView(state, currentPlayers, maxPlayers, full, joinable);
        }

        String stateDisplay() {
            return switch (state) {
                case WAITING -> "Waiting for players";
                case STARTING, COUNTDOWN -> "Starting";
                case RUNNING -> "In game";
                case ENDING -> "Ending";
                case RESETTING -> "Resetting arena";
            };
        }

        String stateColor() {
            return switch (state) {
                case WAITING -> "§a";
                case STARTING, COUNTDOWN -> "§e";
                case RUNNING -> "§c";
                case ENDING, RESETTING -> "§7";
            };
        }

        String joiningDisplay() {
            if (joinable) {
                return "§aOpen";
            }

            if (full) {
                return "§cFull";
            }

            return "§cClosed";
        }

        String playerActionLore() {
            if (joinable) {
                return "§aClick to join";
            }

            if (full) {
                return "§cArena is full";
            }

            if (state == GameState.RUNNING) {
                return "§cMatch in progress";
            }

            if (state == GameState.ENDING) {
                return "§7Ending";
            }

            if (state == GameState.RESETTING) {
                return "§7Resetting";
            }

            return "§cClosed for joining";
        }

        Material material() {
            if (!joinable) {
                if (full || state == GameState.RUNNING) {
                    return Material.RED_CONCRETE;
                }

                if (state == GameState.ENDING || state == GameState.RESETTING) {
                    return Material.GRAY_CONCRETE;
                }

                return Material.BARRIER;
            }

            if (state == GameState.STARTING || state == GameState.COUNTDOWN) {
                return Material.YELLOW_CONCRETE;
            }

            return switch (maxPlayers) {
                case 4 -> Material.LIME_CONCRETE;
                case 8 -> Material.CYAN_CONCRETE;
                case 12 -> Material.PURPLE_CONCRETE;
                default -> Material.WHITE_CONCRETE;
            };
        }
    }
}
