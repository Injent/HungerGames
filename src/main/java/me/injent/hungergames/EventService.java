package me.injent.hungergames;

import me.injent.gamelib.util.TitleBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public class EventService implements Listener {

    private final HungerGames plugin;

    public EventService(HungerGames plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {

    }

    @EventHandler
    public void onResourcePackLoaded(PlayerResourcePackStatusEvent e) {

    }

    @EventHandler
    public void onPlayerKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        Player player = (Player) e.getEntity();

        if (killer != null) {
            Title title = new TitleBuilder()
                    .subtitle(Component.translatable("title.title.kill_icon").append(Component.text(" " + player.getCustomName())))
                    .fade(5, 30, 5)
                    .build();
            killer.showTitle(title);
        }

    }
}
