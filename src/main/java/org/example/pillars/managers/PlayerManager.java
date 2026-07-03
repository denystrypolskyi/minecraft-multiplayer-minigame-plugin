package org.example.pillars.managers;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class PlayerManager {

    private final TeleportManager teleportManager;
    private final HudManager hudManager;

    public PlayerManager(TeleportManager teleportManager, HudManager hudManager) {
        this.teleportManager = teleportManager;
        this.hudManager = hudManager;
    }

    public void resetPlayerState(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setLevel(0);
        player.setExp(0f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    public void resetAndReturnToLobby(Player player) {
        resetPlayerState(player);
        teleportManager.teleportToLobby(player);
        hudManager.resetScoreboard(player);
    }

    public void resetAndReturnToLobby(Player player, String lobbyWorldName) {
        resetPlayerState(player);
        if (!teleportManager.teleportToLobby(player, lobbyWorldName)) {
            hudManager.sendLobbyWorldMissing(player, lobbyWorldName);
        }
        hudManager.resetScoreboard(player);
    }
}
