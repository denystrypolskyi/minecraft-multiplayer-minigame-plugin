package org.example.pillars;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.example.pillars.command.PillarsCommand;
import org.example.pillars.listeners.GameSessionPlayerListener;
import org.example.pillars.listeners.GuiListener;
import org.example.pillars.managers.*;

public final class PillarsPlugin extends JavaPlugin {
    // TODO add duels/VIP rooms
    // TODO add GUI to select arena
    @Override
    public void onEnable() {
        saveDefaultConfig();

        TeleportManager teleportManager = new TeleportManager();
        ItemManager itemManager = new ItemManager(this);
        SoundManager soundManager = new SoundManager();
        HudManager hudManager = new HudManager();
        SpawnManager spawnManager = new SpawnManager();

        ArenaManager arenaManager = new ArenaManager(this);
        StatsManager statsManager = new StatsManager(this);

        PlayerManager playerManager = new PlayerManager(teleportManager, hudManager);

        GameSessionManager gameSessionManager = new GameSessionManager(
                this,
                hudManager,
                playerManager,
                statsManager,
                spawnManager,
                soundManager,
                teleportManager,
                itemManager,
                arenaManager
        );

        getServer().getPluginManager().registerEvents(
                new GameSessionPlayerListener(gameSessionManager),
                this
        );

        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        PluginCommand pillarsCommand = getCommand("pillars");
        if (pillarsCommand == null) {
            getLogger().severe("Command 'pillars' is not defined in plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pillarsCommand.setExecutor(new PillarsCommand(arenaManager, gameSessionManager, hudManager));
    }
}
