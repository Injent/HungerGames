package me.injent.hungergames;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import me.injent.gamelib.GameLib;
import me.injent.gamelib.loot.ChestManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HungerGames extends JavaPlugin {

    private HungerGames plugin;
    private GameLib gameLib;
    private WorldEditPlugin worldEdit;

    @Override
    public void onEnable() {
        gameLib = (GameLib) Bukkit.getPluginManager().getPlugin("GameLib");
        if (gameLib == null) {
            getLogger().warning("Shutdown plugin because GameLib plugin not found!");
            return;
        } else if (getC) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        plugin = this;
        worldEdit = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");

        getConfig().options().copyDefaults(true);
        saveConfig();

        GameManager arena = new GameManager(this);
        // Registering events and commands
        getServer().getPluginManager().registerEvents(arena, this);
        getServer().getPluginManager().registerEvents(new ChestManager(getConfig()), this);
    }

    @Override
    public void onDisable() {
    }

    public GameLib getGameLib() {
        return gameLib;
    }

    public HungerGames getPlugin() {
        return plugin;
    }
}
