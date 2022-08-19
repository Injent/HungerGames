package me.injent.hungergames;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import me.injent.gamelib.GameLib;
import me.injent.gamelib.loot.ChestManager;
import me.injent.gamelib.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class HungerGames extends JavaPlugin {

    private HungerGames plugin;
    private GameLib gameLib;
    private GameManager arena;
    private WorldEditPlugin worldEdit;
    private Map<Integer, TranslatableComponent> messages = new HashMap<>();

    @Override
    public void onEnable() {
        plugin = this;
        worldEdit = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
        gameLib = (GameLib) Bukkit.getPluginManager().getPlugin("GameLib");
        if (gameLib == null) {
            getLogger().warning("Shutdown plugin because GameLib plugin not found!");
            return;
        }
        getConfig().options().copyDefaults(true);
        saveConfig();

        arena = new GameManager(this);
        // Registering events and commands
        getServer().getPluginManager().registerEvents(arena, this);
        getServer().getPluginManager().registerEvents(new ChestManager(getConfig()), this);
        getCommand("start").setExecutor(new CmdService(this));
    }

    @Override
    public void onDisable() {
    }

    public void start() {
        arena.waitingForStart();
    }

    public GameLib getGameLib() {
        return gameLib;
    }

    public HungerGames getPlugin() {
        return plugin;
    }

    private void loadMessages() {
        try {
            File file = new File(plugin.getServer().getPluginsFolder() + getConfig().getString("files.messages"));
            messages = gameLib.getJsonService().getCustomData(file, new TypeReference<>() {});
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Integer j : messages.keySet()) {
            getLogger().info(j + "");
        }
    }

    public void s() {
        TranslatableComponent stats = Component.translatable("tellraw.you_lost")
                .append(Component.translatable("tellraw.coins_earned"))
                .append(Component.translatable("tellraw.players_eleminated"));
        messages.put(55, stats);

        HashMap<Integer, String> js = new HashMap<>();
        js.put(55, GsonComponentSerializer.gson().serialize(stats));
        try {
            gameLib.getJsonService().writeCustomData(new File(plugin.getServer().getPluginsFolder() + getConfig().getString("files.messages")), js);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
