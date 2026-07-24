package org.example.pillars.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.example.pillars.GameSession;
import org.example.pillars.entities.Arena;
import org.example.pillars.enums.GameState;
import org.example.pillars.managers.TranslationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

final class ArenaMenuItemFactory {
    private static final ArenaGroupLayout[] ARENA_GROUPS = {
            new ArenaGroupLayout(
                    4,
                    "§a",
                    Material.LIME_STAINED_GLASS_PANE,
                    1,
                    new int[]{0, 2, 9, 11, 19},
                    new int[]{10, 18, 20}
            ),
            new ArenaGroupLayout(
                    8,
                    "§b",
                    Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    4,
                    new int[]{3, 5, 12, 14, 22},
                    new int[]{13, 21, 23}
            ),
            new ArenaGroupLayout(
                    12,
                    "§d",
                    Material.PURPLE_STAINED_GLASS_PANE,
                    7,
                    new int[]{6, 8, 15, 17, 25},
                    new int[]{16, 24, 26}
            )
    };
    private static final int[] DIVIDER_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int[] OVERFLOW_SLOTS = {37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 49, 50, 51, 52};

    private ArenaMenuItemFactory() {
    }

    static Comparator<Arena> arenaListOrder() {
        return Comparator
                .comparingInt((Arena arena) -> arena.getSpawnPoints().size())
                .thenComparing(Arena::getDisplayName);
    }

    static void fill(Inventory inventory, Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    static ItemStack visualItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    static void placeTriangleArenaItems(
            Inventory inventory,
            List<Arena> arenas,
            Function<Arena, ItemStack> itemFactory,
            TranslationManager translations
    ) {
        List<List<Arena>> groupedArenas = groupArenasByCapacity(arenas);
        List<Arena> overflow = new ArrayList<>();

        for (int i = 0; i < ARENA_GROUPS.length; i++) {
            ArenaGroupLayout group = ARENA_GROUPS[i];
            List<Arena> groupArenas = groupedArenas.get(i);

            placeAccentPanes(inventory, group);
            inventory.setItem(group.headerSlot(), groupHeader(group, translations));
            overflow.addAll(placeIntoSlots(inventory, groupArenas, itemFactory, group.arenaSlots()));
        }

        placeDivider(inventory);
        placeIntoSlots(inventory, overflow, itemFactory, OVERFLOW_SLOTS);
    }

    static ItemStack playerArenaItem(
            Arena arena,
            GameSession session,
            NamespacedKey arenaKey,
            TranslationManager translations
    ) {
        ArenaView view = ArenaView.from(arena, session);
        ItemStack item = new ItemStack(view.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(view.itemColor() + "§l" + arena.getDisplayName());
            meta.setLore(List.of(
                    view.playerActionLore(translations),
                    "",
                    translations.text(
                            "arena-view.status",
                            "value", view.stateColor() + view.stateDisplay(translations)
                    ),
                    translations.text("arena-view.joining", "value", view.joiningDisplay(translations)),
                    translations.text(
                            "arena-view.players",
                            "current", view.currentPlayers(),
                            "maximum", view.maxPlayers()
                    ),
                    translations.text(
                            "arena-view.starts-at",
                            "minimum", arena.getMinPlayers(),
                            "players", translations.plural("units.player-at", arena.getMinPlayers())
                    )
            ));
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getWorldName());
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack adminArenaItem(
            Arena arena,
            GameSession session,
            NamespacedKey actionKey,
            NamespacedKey arenaKey,
            TranslationManager translations
    ) {
        ArenaView view = ArenaView.from(arena, session);
        ItemStack item = new ItemStack(view.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(List.of(
                    translations.text("arena-view.edit-action"),
                    "",
                    translations.text(
                            "arena-view.status",
                            "value", view.stateColor() + view.stateDisplay(translations)
                    ),
                    translations.text("arena-view.joining", "value", view.joiningDisplay(translations)),
                    translations.text(
                            "arena-view.players",
                            "current", view.currentPlayers(),
                            "maximum", view.maxPlayers()
                    ),
                    translations.text(
                            "arena-view.starts-at-capacity",
                            "minimum", arena.getMinPlayers(),
                            "maximum", view.maxPlayers(),
                            "players", translations.plural("units.player-at", view.maxPlayers())
                    ),
                    translations.text(
                            "arena-view.item-cooldown",
                            "seconds", arena.getItemCooldownSeconds()
                    )
            ));
            if (!arena.isJoiningOpen()) {
                lore.add(translations.text("arena-view.admin-lock"));
            }

            meta.setDisplayName(view.itemColor() + "§l" + arena.getDisplayName());
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "edit");
            meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getWorldName());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<Arena> placeIntoSlots(
            Inventory inventory,
            List<Arena> arenas,
            Function<Arena, ItemStack> itemFactory,
            int... slots
    ) {
        int count = Math.min(arenas.size(), slots.length);
        for (int i = 0; i < count; i++) {
            inventory.setItem(slots[i], itemFactory.apply(arenas.get(i)));
        }

        if (arenas.size() <= slots.length) {
            return List.of();
        }

        return arenas.subList(slots.length, arenas.size());
    }

    private static List<List<Arena>> groupArenasByCapacity(List<Arena> arenas) {
        List<Arena> fourPlayerArenas = new ArrayList<>();
        List<Arena> eightPlayerArenas = new ArrayList<>();
        List<Arena> twelvePlayerArenas = new ArrayList<>();

        for (Arena arena : arenas) {
            int size = arena.getSpawnPoints().size();
            if (size <= 4) {
                fourPlayerArenas.add(arena);
            } else if (size <= 8) {
                eightPlayerArenas.add(arena);
            } else {
                twelvePlayerArenas.add(arena);
            }
        }

        return List.of(fourPlayerArenas, eightPlayerArenas, twelvePlayerArenas);
    }

    private static void placeAccentPanes(Inventory inventory, ArenaGroupLayout group) {
        ItemStack pane = blankItem(group.paneMaterial());
        for (int slot : group.accentSlots()) {
            inventory.setItem(slot, pane);
        }
    }

    private static void placeDivider(Inventory inventory) {
        ItemStack pane = blankItem(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : DIVIDER_SLOTS) {
            inventory.setItem(slot, pane);
        }
    }

    private static ItemStack groupHeader(ArenaGroupLayout group, TranslationManager translations) {
        int capacity = group.capacity();
        return visualItem(
                group.paneMaterial(),
                group.color() + "§l" + capacity + " " + translations.plural("units.player", capacity),
                List.of(translations.text(
                        "arena-view.size",
                        "maximum", capacity,
                        "players", translations.plural("units.player", capacity)
                ))
        );
    }

    private static ItemStack blankItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
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

        String stateDisplay(TranslationManager translations) {
            String stateKey = switch (state) {
                case WAITING -> "waiting";
                case STARTING, COUNTDOWN -> "starting";
                case RUNNING -> "running";
                case ENDING -> "ending";
                case RESETTING -> "resetting";
            };
            return translations.text("arena-view.state." + stateKey);
        }

        String stateColor() {
            return switch (state) {
                case WAITING -> "§a";
                case STARTING, COUNTDOWN -> "§e";
                case RUNNING -> "§c";
                case ENDING, RESETTING -> "§7";
            };
        }

        String joiningDisplay(TranslationManager translations) {
            if (joinable) {
                return translations.text("arena-view.joining-state.open");
            }

            if (full) {
                return translations.text("arena-view.joining-state.full");
            }

            return translations.text("arena-view.joining-state.closed");
        }

        String playerActionLore(TranslationManager translations) {
            if (joinable) {
                return translations.text("arena-view.action.join");
            }

            if (full) {
                return translations.text("arena-view.action.full");
            }

            if (state == GameState.RUNNING) {
                return translations.text("arena-view.action.running");
            }

            if (state == GameState.ENDING) {
                return translations.text("arena-view.action.ending");
            }

            if (state == GameState.RESETTING) {
                return translations.text("arena-view.action.resetting");
            }

            return translations.text("arena-view.action.closed");
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

            if (maxPlayers <= 4) {
                return Material.LIME_CONCRETE;
            }

            if (maxPlayers <= 8) {
                return Material.CYAN_CONCRETE;
            }

            return Material.PURPLE_CONCRETE;
        }

        String itemColor() {
            if (joinable) {
                return state == GameState.STARTING || state == GameState.COUNTDOWN ? "§e" : "§a";
            }

            if (full || state == GameState.RUNNING) {
                return "§c";
            }

            if (state == GameState.ENDING || state == GameState.RESETTING) {
                return "§7";
            }

            return "§c";
        }
    }

    private record ArenaGroupLayout(
            int capacity,
            String color,
            Material paneMaterial,
            int headerSlot,
            int[] accentSlots,
            int[] arenaSlots
    ) {
    }
}
