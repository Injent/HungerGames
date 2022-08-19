package me.injent.hungergames.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public class MapOptions {

    private boolean loaded;
    private String resourcesLink;
    private String resourcesSha1;
    private String mapName;
    private String worldName;
    private String game;
    private int shrinkTime;
    private int shrinkDistance;
    private double deathCoins;
    private double winCoins;
    private double openSupplyCoins;
    private double killCoins;
    private double borderSize;

    public void prepopulate(Plugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("options");
        resourcesLink = section.getString("resources");
        resourcesSha1 = section.getString("sha1");
        worldName = section.getString("world_name");
        mapName = section.getString("map_name");
        game = section.getString("game");
        shrinkTime = section.getInt("shrink_time");
        shrinkDistance = section.getInt("shrink_distance");
        ConfigurationSection coins = section.getConfigurationSection("rewards");
        killCoins = coins.getDouble("kill");
        deathCoins = coins.getDouble("death");
        winCoins = coins.getDouble("win");
        openSupplyCoins = coins.getDouble("open_supply");
        borderSize = section.getDouble("border_size");
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getGame() {
        return game;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public String getResourcesLink() {
        return resourcesLink;
    }

    public String getResourcesSha1() {
        return resourcesSha1;
    }

    public String getMapName() {
        return mapName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getShrinkTime() {
        return shrinkTime;
    }

    public int getShrinkDistance() {
        return shrinkDistance;
    }

    public double getDeathCoins() {
        return deathCoins;
    }

    public double getWinCoins() {
        return winCoins;
    }

    public double getOpenSupplyCoins() {
        return openSupplyCoins;
    }

    public double getKillCoins() {
        return killCoins;
    }

    public double getBorderSize() {
        return borderSize;
    }
}
