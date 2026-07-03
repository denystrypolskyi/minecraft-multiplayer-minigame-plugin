package org.example.pillars.managers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.pillars.entities.Arena;
import org.example.pillars.GameSession;

import java.util.HashMap;
import java.util.Map;

public class GameSessionManager {
    private final JavaPlugin plugin;
    private final HudManager hudManager;
    private final PlayerManager playerManager;
    private final StatsManager statsManager;
    private final SpawnManager spawnManager;
    private final SoundManager soundManager;
    private final TeleportManager teleportManager;
    private final ItemManager itemManager;
    private final ArenaManager arenaManager;

    private final Map<String, GameSession> sessions = new HashMap<>();

    public GameSessionManager(
            JavaPlugin plugin,
            HudManager hudManager,
            PlayerManager playerManager,
            StatsManager statsManager,
            SpawnManager spawnManager,
            SoundManager soundManager,
            TeleportManager teleportManager,
            ItemManager itemManager,
            ArenaManager arenaManager
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
    }

    public GameSession getOrCreateSession(Arena arena) {
        return sessions.computeIfAbsent(
                arena.getWorldName(),
                k -> new GameSession(
                        plugin,
                        hudManager,
                        playerManager,
                        statsManager,
                        spawnManager,
                        soundManager,
                        teleportManager,
                        itemManager,
                        arenaManager,
                        arena
                )
        );
    }

    public GameSession getSessionByPlayer(Player player) {
        for (GameSession session : sessions.values()) {
            if (session.hasPlayer(player)) {
                return session;
            }
        }
        return null;
    }

    public void joinSession(Player player, Arena arena) {
        GameSession current = getSessionByPlayer(player);
        if (current != null) {
            current.playerLeave(player);
        }

        GameSession target = getOrCreateSession(arena);
        target.playerJoin(player);
    }

    public void leaveSession(Player player) {

        GameSession session = getSessionByPlayer(player);

        if (session != null) {
            session.playerLeave(player);
        }
    }

    public GameSession getSession(Arena arena) {
        return sessions.get(arena.getWorldName());
    }
}
