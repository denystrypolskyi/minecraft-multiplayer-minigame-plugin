package org.example.pillars;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.example.pillars.entities.Arena;
import org.example.pillars.enums.GameState;
import org.example.pillars.managers.*;

import java.util.*;

public class GameSession {
    private final Arena arena;

    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, Location> frozenPlayers = new HashMap<>();
    private final Map<UUID, UUID> lastDamagerMap = new HashMap<>();
    private final Map<UUID, Location> occupiedSpawns = new HashMap<>();

    private final HudManager hudManager;
    private final PlayerManager playerManager;
    private final StatsManager statsManager;
    private final SpawnManager spawnManager;
    private final SoundManager soundManager;
    private final TeleportManager teleportManager;
    private final ItemManager itemManager;
    private final ArenaManager arenaManager;
    private final JavaPlugin plugin;

    private BukkitTask beginGameCountdownTask;
    private BukkitTask waitingForPlayersTask;
    private BukkitTask itemDistributionTask;
    private BukkitTask witherCountdownTask;
    private BukkitTask witherEffectTask;
    private BukkitTask witherStartDelayTask;
    private BukkitTask arenaResetDelayTask;

    private final Map<UUID, BukkitTask> endGameCountdownTasks = new HashMap<>();

    private GameState state = GameState.WAITING;

    private boolean resetInProgress = false;
    private boolean forceStart = false;
    private long borderShrinkEndTimeMillis = -1L;
    private double borderShrinkBlocksPerSecond = 0.0;

    private final int minPlayers;
    private final int beginCountdownSeconds;
    private final int endGameLobbyCountdownSeconds;
    private final long endGameSpectatorDelayTicks;
    private final long arenaResetDelayTicks;
    private final long borderShrinkSeconds;
    private final double borderMinSize;
    private final String lobbyWorldName;
    private final int witherCountdownSeconds;
    private final int witherEffectDurationTicks;
    private final long witherEffectPeriodTicks;
    private final int witherEffectAmplifier;

    public GameSession(
            JavaPlugin plugin,
            HudManager hudManager,
            PlayerManager playerManager,
            StatsManager statsManager,
            SpawnManager spawnManager,
            SoundManager soundManager,
            TeleportManager teleportManager,
            ItemManager itemManager,
            ArenaManager arenaManager,
            Arena arena
    ) {
        this.plugin = plugin;
        this.hudManager = hudManager;
        this.playerManager = playerManager;
        this.statsManager = statsManager;
        this.spawnManager = spawnManager;
        this.soundManager = soundManager;
        this.teleportManager = teleportManager;
        this.itemManager = itemManager;
        this.arenaManager = arenaManager;
        this.arena = arena;

        this.minPlayers = Math.max(1, arena.getMinPlayers());
        this.beginCountdownSeconds = Math.max(1, plugin.getConfig().getInt("settings.beginCountdownSeconds", 5));
        this.endGameLobbyCountdownSeconds = Math.max(1, plugin.getConfig().getInt("settings.endGameLobbyCountdownSeconds", 5));
        this.endGameSpectatorDelayTicks = Math.max(0L, plugin.getConfig().getLong("settings.endGameSpectatorDelayTicks", 40L));
        this.arenaResetDelayTicks = Math.max(1L, plugin.getConfig().getLong("settings.arenaResetDelayTicks", 160L));
        this.borderShrinkSeconds = Math.max(1L, plugin.getConfig().getLong("settings.borderShrinkSeconds", 300L));
        this.borderMinSize = Math.max(1.0, plugin.getConfig().getDouble("settings.borderMinSize", 1.0));
        this.lobbyWorldName = plugin.getConfig().getString("settings.lobbyWorldName", "world");
        this.witherCountdownSeconds = Math.max(1, plugin.getConfig().getInt("settings.witherCountdownSeconds", 5));
        this.witherEffectDurationTicks = Math.max(1, plugin.getConfig().getInt("settings.witherEffectDurationTicks", 40));
        this.witherEffectPeriodTicks = Math.max(1L, plugin.getConfig().getLong("settings.witherEffectPeriodTicks", 40L));
        this.witherEffectAmplifier = Math.max(0, plugin.getConfig().getInt("settings.witherEffectAmplifier", 1));
    }

    public void playerJoin(Player player) {
        if (!canJoin()) {
            if (arena.getSpawnPoints() == null || arena.getSpawnPoints().isEmpty()) {
                hudManager.sendArenaConfigurationError(player);
            } else if (activePlayers.size() >= arena.getSpawnPoints().size()) {
                hudManager.sendNoSpawnAvailable(player);
            } else {
                hudManager.sendGameAlreadyStartedTitle(player);
            }
            return;
        }

        playerManager.resetPlayerState(player);
        if (!teleportToSpawn(player)) {
            playerManager.resetAndReturnToLobby(player, lobbyWorldName);
            return;
        }
        addActivePlayer(player);
        hudManager.sendPlayerJoinedArena(getAllPlayerIds(), player, arena.getDisplayName(), activePlayers.size(), arena.getSpawnPoints().size());

        updateArenaHudForAllPlayers();

        hudManager.updatePlayerScoreboard(player, getAllPlayerIds().size(), arena.getSpawnPoints().size(), state, arena.getDisplayName(), statsManager.getStats(player.getUniqueId()).getKills(), statsManager.getStats(player.getUniqueId()).getWins());
        if (state == GameState.WAITING && activePlayers.size() < minPlayers) {
            startWaitingForPlayersTask();
        }
        startBeginGameCountdown();
    }

    private void removePlayer(Player player, boolean isDisconnect) {
        UUID uuid = player.getUniqueId();

        BukkitTask task = endGameCountdownTasks.remove(uuid);
        if (task != null) task.cancel();

        boolean wasActive = activePlayers.contains(uuid);

        if (isDisconnect && !wasActive && !spectators.contains(uuid)) return;

        activePlayers.remove(uuid);
        spectators.remove(uuid);
        lastDamagerMap.remove(uuid);

        if (state == GameState.WAITING || state == GameState.STARTING) {
            Location spawn = occupiedSpawns.remove(uuid);
            spawnManager.cleanupSpawn(spawn);

            if (state == GameState.STARTING && activePlayers.size() < minPlayers) {
                state = GameState.WAITING;
                forceStart = false;
                updateArenaHudForAllPlayers();
                startWaitingForPlayersTask();
            }
        }

        playerManager.resetAndReturnToLobby(player, lobbyWorldName);
        hudManager.cleanupPlayerScoreboard(player);
        updateArenaHudForAllPlayers();
        if (state == GameState.WAITING && !activePlayers.isEmpty()) {
            startWaitingForPlayersTask();
        } else if (activePlayers.isEmpty()) {
            cancelWaitingForPlayersTask();
        }

        if (state == GameState.RUNNING) {
            evaluateGameEnd();
        }
    }

    public void playerLeave(Player player) {
        removePlayer(player, false);
    }

    public void playerDisconnect(Player player) {
        removePlayer(player, true);
    }


    public void playerDeath(Player dead, Player killer) {
        UUID uuid = dead.getUniqueId();
        if (!activePlayers.contains(uuid)) return;

        BukkitTask endGameCountdownTask = endGameCountdownTasks.remove(uuid);
        if (endGameCountdownTask != null) {
            endGameCountdownTask.cancel();
        }

        rewardKiller(killer);

        setPlayerAsSpectator(dead);

        soundManager.playLoseSound(dead);

        evaluateGameEnd();
    }

    public boolean forceStart(Player startedBy) {
        if (state != GameState.WAITING) {
            return false;
        }

        forceStart = true;
        hudManager.broadcastForceStartedArena(startedBy, arena.getDisplayName());

        if (beginGameCountdownTask == null) {
            startBeginGameCountdown();
        }

        return true;
    }

    private void handleGameEnd(Player winner) {
        if (state == GameState.ENDING || state == GameState.RESETTING) return;

        stopSessionTasks();
        state = GameState.ENDING;
        updateArenaHudForAllPlayers();

        Set<UUID> allPlayersSnapshot = new HashSet<>(activePlayers);
        allPlayersSnapshot.addAll(spectators);

        for (UUID uuid : new ArrayList<>(activePlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(arena.getSpectatorCenter());
                hudManager.sendSpectatorTitle(player);
            }
            spectators.add(uuid);
        }
        activePlayers.clear();

        if (winner != null) {
            statsManager.incrementWins(winner.getUniqueId());
            hudManager.updatePlayerStats(winner, statsManager.getStats(winner.getUniqueId()).getKills(), statsManager.getStats(winner.getUniqueId()).getWins());
            hudManager.broadcastWinner(winner.getName(), arena.getDisplayName());
            for (UUID uuid : allPlayersSnapshot) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    soundManager.playWinSound(player);
                    hudManager.sendWinnerTitle(player, winner.getName());
                }
            }
        } else {
            for (UUID uuid : allPlayersSnapshot) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    soundManager.playLoseSound(player);
                    hudManager.sendNoWinnerTitle(player);
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : allPlayersSnapshot) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) startEndGameCountdown(player, endGameLobbyCountdownSeconds);
            }
        }, endGameSpectatorDelayTicks);

        arenaResetDelayTask = Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, arenaResetDelayTicks);
    }

    public void startEndGameCountdown(Player player, int seconds) {
        final int[] timeLeft = {seconds};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                BukkitTask t = endGameCountdownTasks.remove(player.getUniqueId());
                if (t != null) t.cancel();
                spectators.remove(player.getUniqueId());
                return;
            }

            if (timeLeft[0] > 0) {
                hudManager.sendReturnToLobbyTitle(player, timeLeft[0]);
                timeLeft[0]--;
            } else {
                player.setGameMode(GameMode.SURVIVAL);
                playerManager.resetAndReturnToLobby(player, lobbyWorldName);
                hudManager.resetScoreboard(player);

                spectators.remove(player.getUniqueId());

                BukkitTask t = endGameCountdownTasks.remove(player.getUniqueId());
                if (t != null) t.cancel();
            }
        }, 0L, 20L);

        endGameCountdownTasks.put(player.getUniqueId(), task);
    }


    private void evaluateGameEnd() {
        if (state != GameState.RUNNING) return;

        if (activePlayers.isEmpty()) {
            handleGameEnd(null);
            return;
        }

        if (activePlayers.size() == 1) {
            UUID winnerId = activePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerId);
            handleGameEnd(winner);
        }
    }

    private void rewardKiller(Player killer) {
        if (killer == null) return;

        UUID uuid = killer.getUniqueId();
        statsManager.incrementKills(uuid);

        int kills = statsManager.getStats(uuid).getKills();
        int wins = statsManager.getStats(uuid).getWins();
        hudManager.updatePlayerStats(killer, kills, wins);
    }

    private void setPlayerAsSpectator(Player player) {
        UUID uuid = player.getUniqueId();

        activePlayers.remove(uuid);
        spectators.add(uuid);

        frozenPlayers.remove(uuid);
        lastDamagerMap.remove(uuid);

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(arena.getSpectatorCenter());

        hudManager.sendSpectatorTitle(player);
    }

    private boolean teleportToSpawn(Player player) {
        if (arena.getSpawnPoints() == null || arena.getSpawnPoints().isEmpty()) {
            hudManager.sendArenaConfigurationError(player);
            return false;
        }

        Location spawn = spawnManager.getFarthestSpawn(arena, getActivePlayerIds(), occupiedSpawns.values());
        if (spawn == null) {
            hudManager.sendNoSpawnAvailable(player);
            return false;
        }

        spawnManager.prepareSpawn(spawn);

        occupiedSpawns.put(player.getUniqueId(), spawn);

        Location teleportedLoc = spawn.clone().add(0.5, 1, 0.5);
        teleportManager.teleportToSpawnPoint(player, teleportedLoc);

        frozenPlayers.put(player.getUniqueId(), teleportedLoc);
        return true;
    }

    private void resetGame() {
        if (resetInProgress) return;

        resetInProgress = true;
        state = GameState.RESETTING;
        updateArenaHudForAllPlayers();

        resetSession();
        resetArenaInternal();
    }

    private void resetArenaInternal() {
        arenaManager.resetArena(arena, () -> {
            state = GameState.WAITING;
            resetInProgress = false;
        });
    }

    private void resetSession() {
        stopSessionTasks();
        clearSessionState();
    }


    private void clearSessionState() {
        frozenPlayers.clear();
        activePlayers.clear();
        spectators.clear();
        lastDamagerMap.clear();
        endGameCountdownTasks.clear();
        occupiedSpawns.clear();
        forceStart = false;
    }

    private void stopSessionTasks() {
        cancelBeginGameCountdownTask();
        cancelWaitingForPlayersTask();
        cancelItemDistributionTask();
        cancelWitherTask();
        cancelFinalZoneTask();
        cancelWitherStartDelayTask();
        cancelArenaResetDelayTask();

        cancelEndGameCountdownTasks();

        stopWorldBorder();
    }

    private boolean canJoin() {
        return state != GameState.RUNNING
                && state != GameState.RESETTING
                && state != GameState.ENDING
                && arena.getSpawnPoints() != null
                && activePlayers.size() < arena.getSpawnPoints().size();
    }

    public Set<UUID> getActivePlayerIds() {
        return Collections.unmodifiableSet(activePlayers);
    }

    public Set<UUID> getAllPlayerIds() {
        Set<UUID> all = new HashSet<>(activePlayers);
        all.addAll(spectators);
        return Collections.unmodifiableSet(all);
    }

    private void addActivePlayer(Player player) {
        activePlayers.add(player.getUniqueId());
    }

    public boolean hasPlayer(Player player) {
        return getAllPlayerIds().contains(player.getUniqueId());
    }

    public boolean isPlayerFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    public Location getFrozenPlayerLocation(Player player) {
        return frozenPlayers.get(player.getUniqueId());
    }

    public void setLastDamager(UUID victim, UUID damager) {
        lastDamagerMap.put(victim, damager);
    }

    public UUID getLastDamager(UUID victim) {
        return lastDamagerMap.get(victim);
    }

    public Arena getArena() {
        return arena;
    }

    public GameState getState() {
        return state;
    }

    private void updateArenaHudForAllPlayers() {
        hudManager.updateArenaInfoForAllPlayers(
                getAllPlayerIds(),
                getAllPlayerIds().size(),
                arena.getSpawnPoints().size(),
                state,
                arena.getDisplayName()
        );
    }

    public void startBeginGameCountdown() {
        if (state != GameState.WAITING) return;
        if ((activePlayers.size() < minPlayers && !forceStart) || beginGameCountdownTask != null) return;

        state = GameState.STARTING;
        cancelWaitingForPlayersTask();
        updateArenaHudForAllPlayers();
        final int[] counter = {beginCountdownSeconds};

        beginGameCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activePlayers.isEmpty()) {
                cancelBeginGameCountdownTask();
                return;
            }

            if (!forceStart && activePlayers.size() < minPlayers) {
                cancelBeginGameCountdownTask();
                state = GameState.WAITING;
                updateArenaHudForAllPlayers();
                startWaitingForPlayersTask();
                for (UUID uuid : activePlayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        hudManager.sendNotEnoughPlayersTitle(player);
                    }
                }

                return;
            }

            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    hudManager.sendCountdownTitle(player, counter[0]);
                    soundManager.playCountdownTickSound(player);
                }
            }

            counter[0]--;
            if (counter[0] < 0) {
                state = GameState.RUNNING;
                frozenPlayers.clear();
                cancelBeginGameCountdownTask();
                updateArenaHudForAllPlayers();

                for (UUID uuid : getActivePlayerIds()) {
                    if (uuid == null) {
                        continue;
                    }
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) {
                        continue;
                    }
                    hudManager.sendGameStartTitle(player);
                    soundManager.playGameStartSound(player);
                }

                startWorldBorder();
                if (!forceStart) {
                    hudManager.broadcastGameStarted(arena.getDisplayName());
                }
                startItemDistributionTask();
            }
        }, 0L, 20L);
    }

    private void startItemDistributionTask() {
        if (itemDistributionTask != null) return;

        final int interval = getArena().getItemCooldownSeconds();
        final int[] counter = {interval};

        itemDistributionTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    double currentBorderSize = getCurrentBorderSize();
                    hudManager.sendItemCooldown(player, counter[0], getSecondsUntilNextBorderDecrease(currentBorderSize), currentBorderSize);
                }
            }

            if (counter[0] == interval) {
                for (UUID uuid : activePlayers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        itemManager.giveRandomItem(player);
                        soundManager.playItemGivenSound(player);
                    }
                }
            }

            counter[0]--;
            if (counter[0] <= 0) counter[0] = interval;
        }, 0L, 20L);
    }

    private void startWaitingForPlayersTask() {
        if (waitingForPlayersTask != null) return;

        waitingForPlayersTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.WAITING || activePlayers.isEmpty()) {
                cancelWaitingForPlayersTask();
                return;
            }

            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    hudManager.sendWaitingForPlayers(player, activePlayers.size(), minPlayers);
                }
            }
        }, 0L, 20L);
    }


    private void startWitherCountdown() {
        if (state != GameState.RUNNING) return;

        final int[] counter = {witherCountdownSeconds};

        witherCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (activePlayers.isEmpty()) {
                cancelFinalZoneTask();
                return;
            }

            for (UUID uuid : getActivePlayerIds()) {
                if (uuid == null) {
                    continue;
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                hudManager.sendWitherCountdownTitle(player, counter[0]);
                soundManager.playCountdownTickSound(player);
            }

            counter[0]--;

            if (counter[0] < 0) {
                cancelFinalZoneTask();
                for (UUID uuid : getActivePlayerIds()) {
                    if (uuid == null) {
                        continue;
                    }
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) {
                        continue;
                    }
                    hudManager.sendWitherStartTitle(player);
                    soundManager.playWitherStartSound(player);

                }
                startWitherTask();
            }

        }, 0L, 20L);
    }

    private void startWitherTask() {
        if (state != GameState.RUNNING) return;

        witherEffectTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (activePlayers.isEmpty()) {
                cancelWitherTask();
                return;
            }

            for (UUID uuid : getActivePlayerIds()) {
                if (uuid == null) {
                    continue;
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER,
                        witherEffectDurationTicks,
                        witherEffectAmplifier,
                        false,
                        true
                ));
            }

        }, 0L, witherEffectPeriodTicks);
    }

    public void startWorldBorder() {
        List<Location> spawns = arena.getSpawnPoints();
        if (spawns.isEmpty()) return;

        World world = spawns.get(0).getWorld();
        if (world == null) return;

        double sumX = 0, sumY = 0, sumZ = 0;
        for (Location spawn : spawns) {
            sumX += spawn.getX() + 0.5;
            sumY += spawn.getY() + 1;
            sumZ += spawn.getZ() + 0.5;
        }
        double avgX = sumX / spawns.size();
        double avgY = sumY / spawns.size();
        double avgZ = sumZ / spawns.size();
        Location borderCenter = new Location(world, avgX, avgY, avgZ);

        double maxDistance = 0;
        for (Location spawn : spawns) {
            double dx = (spawn.getX() + 0.5) - avgX;
            double dz = (spawn.getZ() + 0.5) - avgZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            maxDistance = Math.max(maxDistance, distance);
        }

        double borderPadding = Math.max(2, spawns.size() / 4);
        double initialSize = (maxDistance + borderPadding) * 2;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(borderCenter);
        border.setSize(initialSize);
        border.setDamageAmount(1.0);
        border.setDamageBuffer(0);
        border.setWarningDistance(0);
        border.setWarningTime(0);

        border.setSize(borderMinSize, borderShrinkSeconds);
        borderShrinkEndTimeMillis = System.currentTimeMillis() + (borderShrinkSeconds * 1000L);
        borderShrinkBlocksPerSecond = Math.max(0.0, (initialSize - borderMinSize) / borderShrinkSeconds);

        witherStartDelayTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                this::startWitherCountdown,
                borderShrinkSeconds * 20L
        );
    }


    private void cancelEndGameCountdownTasks() {
        for (BukkitTask task : endGameCountdownTasks.values()) {
            if (task != null) task.cancel();
        }

        endGameCountdownTasks.clear();
    }


    private void cancelWitherTask() {
        if (witherEffectTask != null) {
            witherEffectTask.cancel();
            witherEffectTask = null;
        }
    }

    private void cancelFinalZoneTask() {
        if (witherCountdownTask != null) {
            witherCountdownTask.cancel();
            witherCountdownTask = null;
        }
    }

    private void cancelWitherStartDelayTask() {
        if (witherStartDelayTask != null) {
            witherStartDelayTask.cancel();
            witherStartDelayTask = null;
        }
    }

    private void cancelArenaResetDelayTask() {
        if (arenaResetDelayTask != null) {
            arenaResetDelayTask.cancel();
            arenaResetDelayTask = null;
        }
    }


    private void cancelBeginGameCountdownTask() {
        if (beginGameCountdownTask != null) {
            beginGameCountdownTask.cancel();
            beginGameCountdownTask = null;
        }
    }

    private void cancelWaitingForPlayersTask() {
        if (waitingForPlayersTask != null) {
            waitingForPlayersTask.cancel();
            waitingForPlayersTask = null;
        }
    }

    private void cancelItemDistributionTask() {
        if (itemDistributionTask != null) {
            itemDistributionTask.cancel();
            itemDistributionTask = null;
        }
    }

    private void stopWorldBorder() {
        borderShrinkEndTimeMillis = -1L;
        borderShrinkBlocksPerSecond = 0.0;
        if (!arena.getSpawnPoints().isEmpty()) {
            World world = arena.getSpawnPoints().getFirst().getWorld();
            if (world != null) {
                WorldBorder border = world.getWorldBorder();
                border.setSize(1000);
                border.setCenter(new Location(world, 0, 64, 0));
                border.setDamageAmount(0.2);
                border.setDamageBuffer(5);
            }
        }
    }

    private long getSecondsUntilNextBorderDecrease(double currentSize) {
        if (borderShrinkEndTimeMillis <= 0L) {
            return 0L;
        }

        if (borderShrinkBlocksPerSecond <= 0.0 || currentSize <= borderMinSize) {
            return 0L;
        }

        double visibleSize = Math.ceil(currentSize);
        double minVisibleSize = Math.ceil(borderMinSize);
        if (visibleSize <= minVisibleSize) {
            return 0L;
        }

        double nextVisibleSize = Math.max(minVisibleSize, visibleSize - 1.0);
        double seconds = (currentSize - nextVisibleSize) / borderShrinkBlocksPerSecond;
        return Math.max(1L, (long) Math.ceil(seconds));
    }

    private double getCurrentBorderSize() {
        if (arena.getSpawnPoints().isEmpty()) {
            return borderMinSize;
        }

        World world = arena.getSpawnPoints().getFirst().getWorld();
        if (world == null) {
            return borderMinSize;
        }

        return world.getWorldBorder().getSize();
    }


}
