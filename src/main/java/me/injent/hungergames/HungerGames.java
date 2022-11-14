package me.injent.hungergames;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import me.injent.gamelib.GameLib;
import me.injent.gamelib.loot.ChestManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class HungerGames extends JavaPlugin {

    private HungerGames plugin;
    private GameLib gameLib;
    private WorldEditPlugin worldEdit;

    @Override
    public void onEnable() {
        // Request permission of GameLib
        File file = new File(getServer().getPluginsFolder() + "/GameLib/config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.getBoolean("plugins." + getName())) {
            getLogger().info(getName() + " start cancelled (it's normal)");
            return;
        }
        try {
            config.set("plugins." + getName(), false);
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Init
        gameLib = (GameLib) Bukkit.getPluginManager().getPlugin("GameLib");
        if (gameLib == null) {
            getLogger().warning("Shutdown plugin because GameLib plugin not found!");
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

//        gameLib = (GameLib) Bukkit.getPluginManager().getPlugin("GameLib");
//        if (gameLib == null) {
//            getLogger().warning("Shutdown plugin because GameLib plugin not found!");
//            return;
//        }
//        if (gameLib.getOptions().getGameNumber() == 0) {
//            getServer().getPluginManager().disablePlugin(this);
//            gameLib.getOptions().addGameNumber(1);
//            return;
//        }
//        plugin = this;
//        worldEdit = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
//
//        getConfig().options().copyDefaults(true);
//        saveConfig();
//
//        GameManager arena = new GameManager(this);
//        // Registering events and commands
//        getServer().getPluginManager().registerEvents(arena, this);
//        getServer().getPluginManager().registerEvents(new ChestManager(getConfig()), this);
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
