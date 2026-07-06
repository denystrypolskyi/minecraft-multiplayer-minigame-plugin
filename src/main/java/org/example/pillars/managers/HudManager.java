package org.example.pillars.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.example.pillars.enums.GameState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HudManager {
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Map<String, Team>> playerTeams = new HashMap<>();

    private static final String[] UNIQUE_ENTRIES = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
            "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r", "§f§r"
    };

    private int blankCounter = 0;

    private static final int FADE_IN = 5;
    private static final int FADE_OUT = 5;

    private static final int SHORT_STAY = 25;
    private static final int MEDIUM_STAY = 30;
    private static final int LONG_STAY = 50;

    private void initializeScoreboard(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (playerScoreboards.containsKey(uuid)) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("pillarshud", "dummy", "§6§lPILLARS");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Map<String, Team> teams = new LinkedHashMap<>();

        String[] lineKeys = {
                "infoHeader",
                "playerLine",
                "onlineLine",
                "statusLine",
                "arenaLine",
                "statHeader",
                "killsLine",
                "winsLine"
        };

        int score = 11;

        obj.getScore(nextBlank()).setScore(score--);

        for (String key : lineKeys) {
            Team team = board.registerNewTeam("hud_" + key);
            String entry = UNIQUE_ENTRIES[teams.size() % UNIQUE_ENTRIES.length];
            team.addEntry(entry);

            if (key.equals("statHeader")) {
                obj.getScore(nextBlank()).setScore(score--);
            }

            obj.getScore(entry).setScore(score--);
            teams.put(key, team);
        }

        obj.getScore(nextBlank()).setScore(score--);

        playerScoreboards.put(uuid, board);
        playerTeams.put(uuid, teams);

        player.setScoreboard(board);
    }

    private String nextBlank() {
        return UNIQUE_ENTRIES[blankCounter++ % UNIQUE_ENTRIES.length] + " ";
    }

    public void updatePlayerScoreboard(Player player, int players, int maxPlayers, GameState state,
                                       String arenaName, int kills, int wins) {
        initializeScoreboard(player);

        UUID uuid = player.getUniqueId();
        Map<String, Team> teams = playerTeams.get(uuid);
        if (teams == null) return;

        teams.get("infoHeader").setPrefix("§6§lINFO  ");

        teams.get("playerLine").setPrefix("§a✦ §fPlayer: ");
        teams.get("playerLine").setSuffix("§a" + player.getName());

        teams.get("onlineLine").setPrefix("§b⬤ §fOnline: ");
        teams.get("onlineLine").setSuffix("§b" + players + "§7/§b" + maxPlayers);

        teams.get("statusLine").setPrefix("§d◆ §fStatus: ");
        teams.get("statusLine").setSuffix(formatState(state));

        teams.get("arenaLine").setPrefix("§e⚔ §fArena: ");
        teams.get("arenaLine").setSuffix("§e" + arenaName);

        teams.get("statHeader").setPrefix("§c§lSTATS  ");

        teams.get("killsLine").setPrefix("§4☠ §fKills: ");
        teams.get("killsLine").setSuffix("§4" + kills);

        teams.get("winsLine").setPrefix("§6★ §fWins: ");
        teams.get("winsLine").setSuffix("§6" + wins);
    }

    public void updateArenaInfoForAllPlayers(Set<UUID> playersSet, int activeCount, int max, GameState state, String arenaName) {
        for (UUID uuid : playersSet) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            Map<String, Team> teams = playerTeams.get(uuid);
            if (teams == null) continue;

            Team online = teams.get("onlineLine");
            if (online != null) {
                online.setSuffix("§b" + activeCount + "§7/§b" + max);
            }

            Team status = teams.get("statusLine");
            if (status != null) {
                status.setSuffix(formatState(state));
            }

            Team arena = teams.get("arenaLine");
            if (arena != null) {
                arena.setSuffix("§e" + arenaName);
            }
        }
    }

    public void updatePlayerStats(Player player, int kills, int wins) {
        UUID uuid = player.getUniqueId();
        Map<String, Team> teams = playerTeams.get(uuid);
        if (teams == null) return;

        Team killsTeam = teams.get("killsLine");
        if (killsTeam != null) {
            killsTeam.setSuffix("§4" + kills);
        }

        Team winsTeam = teams.get("winsLine");
        if (winsTeam != null) {
            winsTeam.setSuffix("§6" + wins);
        }
    }

    public void cleanupPlayerScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        playerScoreboards.remove(uuid);
        playerTeams.remove(uuid);
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public void resetScoreboard(Player player) {
        cleanupPlayerScoreboard(player);
    }

    public void sendReturnToLobbyTitle(Player player, int seconds) {
        player.sendTitle("§eReturning to lobby", "§fin §a" + seconds + " §fsec.", 0, MEDIUM_STAY, 0);
    }

    public void sendWinnerTitle(Player player, String winnerName) {
        player.sendTitle("§6§lWINNER", "§e" + winnerName + " §7won the game!", FADE_IN, LONG_STAY, FADE_OUT);
    }

    public void sendCountdownTitle(Player player, int secondsLeft) {
        player.sendTitle("§6§l" + secondsLeft, "§fUntil the game starts", FADE_IN, MEDIUM_STAY, FADE_OUT);
    }

    public void sendGameStartTitle(Player player) {
        player.sendTitle("§aThe game has started!", "§fGood luck!", FADE_IN, LONG_STAY, FADE_OUT);
    }

    public void sendArenaResettingTitle(Player player) {
        player.sendTitle("§6§lARENA", "§eResetting... §7Please wait", FADE_IN, MEDIUM_STAY, FADE_OUT);
    }

    public void sendGameAlreadyStartedTitle(Player player) {
        player.sendTitle("§c§lERROR", "§fThe game has already started!", FADE_IN, SHORT_STAY, FADE_OUT);
    }

    public void sendItemCooldown(Player player, int secondsLeft, long secondsUntilNextZoneDecrease, double zoneSize) {
        String zoneTimer = secondsUntilNextZoneDecrease > 0
                ? "§cZone -1 block in §e" + secondsUntilNextZoneDecrease + "s"
                : "§cZone: §4Final size";

        player.sendActionBar("§eItem in §a" + secondsLeft + "s §8| " + zoneTimer + " §8| §bSize: §f" + Math.ceil(zoneSize));
    }

    public void sendNotEnoughPlayersTitle(Player player) {
        player.sendTitle("§cNot enough players!", "§fThe game has stopped.", FADE_IN, MEDIUM_STAY, FADE_OUT);
    }

    public void sendWaitingForPlayers(Player player, int currentPlayers, int minPlayers) {
        int needed = Math.max(0, minPlayers - currentPlayers);
        String status = needed == 0
                ? "§aStarting soon"
                : "§eWaiting for §f" + needed + "§e more player" + (needed == 1 ? "" : "s");

        player.sendActionBar(status + " §8| §bPlayers: §f" + currentPlayers + "§7/§f" + minPlayers);
    }

    public void sendSpectatorTitle(Player player) {
        player.sendTitle("§cYou lost", "§7You have been eliminated", FADE_IN, MEDIUM_STAY, FADE_OUT);
    }

    public void sendArenaNotFound(Player player) {
        player.sendMessage("§cArena not found!");
    }

    public void sendLeftArena(Player player) {
        player.sendMessage("§eYou left the arena.");
    }

    public void sendNotInGame(Player player) {
        player.sendMessage("§cYou are not in a game.");
    }

    public void sendGameAlreadyRunning(Player player) {
        player.sendMessage("§cThe game is already running.");
    }

    public void sendForceStartSuccess(Player player) {
        player.sendMessage("§aThe game has been force-started!");
    }

    public void sendPlayerJoinedArena(Set<UUID> recipients, Player player, String arenaName, int currentPlayers, int maxPlayers) {
        String message = "§6§lPILLARS §8» §a" + player.getName() + " §7joined §e" + arenaName
                + " §8(§b" + currentPlayers + "§7/§b" + maxPlayers + "§8)";

        for (UUID uuid : recipients) {
            Player recipient = Bukkit.getPlayer(uuid);
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage(message);
            }
        }
    }

    public void broadcastForceStartedArena(Player player, String arenaName) {
        Bukkit.broadcastMessage("§6§lPILLARS §8» §e" + arenaName + " §ahas been force-started §7by §f" + player.getName());
    }

    public void broadcastGameStarted(String arenaName) {
        Bukkit.broadcastMessage("§6§lPILLARS §8» §aThe game has started on §e" + arenaName + "§a. §7Good luck!");
    }

    public void broadcastWinner(String winnerName, String arenaName) {
        Bukkit.broadcastMessage("§6§lPILLARS §8» §e" + winnerName + " §6won §e" + arenaName + "§6! §aCongratulations!");
    }

    public void sendNoWinnerTitle(Player player) {
        player.sendTitle("§c§lNO WINNER", "§7All players have been eliminated", FADE_IN, LONG_STAY, FADE_OUT);
    }

    public void sendNoPermission(Player player) {
        player.sendMessage("§cYou do not have permission to use this command.");
    }

    public void sendCommandUsage(Player player) {
        player.sendMessage("§eUsage: §f/pillars <join|quickjoin|leave|forcestart|menu|admin>");
    }

    public void sendJoinUsage(Player player) {
        player.sendMessage("§eUsage: §f/pillars join <arena>");
    }

    public void sendNoJoinableSession(Player player) {
        player.sendMessage("§cNo joinable arena was found.");
    }

    public void sendItemAddUsage(Player player) {
        player.sendMessage("§eUsage: §f/pillars itemadd <common|rare|legendary> [weight]");
    }

    public void sendItemRemoveUsage(Player player) {
        player.sendMessage("§eUsage: §f/pillars itemremove <common|rare|legendary> <material>");
    }

    public void sendHoldItemToConfigure(Player player) {
        player.sendMessage("§cHold the item you want to configure in your main hand.");
    }

    public void sendUnknownMaterial(Player player) {
        player.sendMessage("§cUnknown material.");
    }

    public void sendItemConfigured(Player player, Material material, String rarity, int weight) {
        player.sendMessage("§6§lPILLARS §8» §aAdded §f" + material.name() + " §ato §e" + rarity.toLowerCase() + " §awith weight §f" + weight + "§a.");
    }

    public void sendItemRemoved(Player player, Material material, String rarity) {
        player.sendMessage("§6§lPILLARS §8» §eDisabled §f" + material.name() + " §ein §f" + rarity.toLowerCase() + "§e items.");
    }

    public void sendAdminConfigUpdated(Player player, int commonPercent, int rarePercent, int legendaryPercent) {
        player.sendMessage("§6§lPILLARS §8» §aRarity updated: §7Common §f" + commonPercent + "% §8| §bRare §f" + rarePercent + "% §8| §6Legendary §f" + legendaryPercent + "%");
    }

    public void sendNoSpawnAvailable(Player player) {
        player.sendMessage("§cThere are no available spawn points in this arena.");
    }

    public void sendArenaConfigurationError(Player player) {
        player.sendMessage("§cThe arena is configured incorrectly. Please contact an administrator.");
    }

    public void sendLobbyWorldMissing(Player player, String worldName) {
        player.sendMessage("§cLobby world '" + worldName + "' was not found.");
    }

    public void sendWitherCountdownTitle(Player player, int secondsLeft) {
        player.sendTitle("§c§lFINAL ZONE", "§7Poison starts in §e§l" + secondsLeft + " §7sec.", FADE_IN, MEDIUM_STAY, FADE_OUT);
    }

    public void sendWitherStartTitle(Player player) {
        player.sendTitle("§5§lWARNING!", "§cThe Wither phase has started!", FADE_IN, LONG_STAY, FADE_OUT);
    }

    private String formatState(GameState state) {
        if (state == null) {
            return "§7Unknown";
        }

        return switch (state) {
            case WAITING -> "§eWaiting for players";
            case STARTING, COUNTDOWN -> "§6Starting";
            case RUNNING -> "§aIn progress";
            case ENDING -> "§cEnding";
            case RESETTING -> "§7Resetting";
        };
    }
}
