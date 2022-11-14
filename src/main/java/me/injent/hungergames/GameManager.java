package me.injent.hungergames;

import me.injent.gamelib.GameLib;
import me.injent.gamelib.board.Board;
import me.injent.gamelib.data.DataUtil;
import me.injent.gamelib.data.GameState;
import me.injent.gamelib.player.PlayerData;
import me.injent.gamelib.teams.SpawnLoc;
import me.injent.gamelib.teams.Team;
import me.injent.gamelib.util.ChatManager;
import me.injent.gamelib.util.TimerRunnable;
import me.injent.gamelib.util.TitleBuilder;
import me.injent.gamelib.util.WorldManager;
import me.injent.hungergames.util.MapOptions;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameManager implements Listener {
    private final HungerGames plugin;
    private GameLib gameLib;

    private World world;
    private WorldManager level;
    private WorldBorder worldBorder;
    private BossBar bossBar;
    private MapOptions options = new MapOptions();

    private List<SpawnLoc> markers;
    private List<UUID> alive = new ArrayList<>();
    private List<UUID> players = new ArrayList<>();
    private Board board;
    private int shrinkStage;

    public GameManager(HungerGames plugin) {
        this.plugin = plugin;
        gameLib = plugin.getGameLib();
        board = gameLib.getBoard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            board.update(player);
        }

        // Loading data
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                markers = gameLib.getDataService().getLocations(plugin);
                options.prepopulate(plugin);
                bossBar = BossBar.bossBar(Component.text(""), 0.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                gameLib.getOptions().setGameState(GameState.STARTING);
                board.setPlaceHolder("game", options.getGame());
                board.setPlaceHolder("map", options.getMapName());
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::init);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void init() {
        // Checking conditions

        List<String> lines = plugin.getConfig().getStringList("scoreboard.waiting");
        lines.set(3, "Ожидание...");
        board.setLines(lines);

        level = new WorldManager(plugin.getDataFolder(), plugin.getConfig().getString("files.world", "world"), () -> {
            world = level.getWorld();
            worldBorder = world.getWorldBorder();
            starting();
        });
        gameLib.getOptions().addGameNumber(1);
    }

    private void starting() {
        // Teleporting player to their markers
        for (SpawnLoc spawnLoc : markers) {
            Team team = DataUtil.getTeam(spawnLoc.getTeamId());
            if (team != null) {
                team.getBukkitPlayers().forEach(player -> player.teleport(spawnLoc.toLocation(world)));
            }
        }

        // Preparing for game
        for (UUID uuid : DataUtil.getPlayersData().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerData.setDefault(player);
                player.setInvulnerable(true);
                players.add(uuid);
                DataUtil.setLocalGameCoins(uuid, 0);
            }
        }
        alive = new ArrayList<>(players);

        // Runt countdown timer
        worldBorder.setSize(options.getBorderSize());

        board.setLines(plugin.getConfig().getStringList("scoreboard.starting"));
        Bukkit.getScheduler().runTask(plugin, new TimerRunnable(plugin, 10, 0, this::start, (s, m) -> {
            if (s == 0 && m == 0) return;
            Bukkit.getServer().showTitle(new TitleBuilder().title(Component.translatable("title.title.starting"))
                .subtitle(Component.translatable("title.subtitle.countdown"))
                .fade(0, 30, 5)
                .build()
            );
            board.setPlaceHolder("time", ChatManager.formatTimer(s, m));
            board.updateAll();

            if (s <= 3 && s != 0) {
                Bukkit.getServer().playSound(Sound.sound(Key.key("countdown"), Sound.Source.MASTER, 1, 1f));
            }
        }));
    }

    private void start() {
        Bukkit.getServer().playSound(Sound.sound(Key.key("start"), Sound.Source.MASTER, 1f, 1f));

        // Remove barriers
        for (SpawnLoc spawnLoc : markers) {
            Location loc = spawnLoc.toLocation(world);
            WorldManager.fillBlocks(loc.clone().add(-2.0,0.0,-2.0), loc.clone().add(2.0,3.0,2.0), Material.AIR);
        }

        for (Team team : DataUtil.getTeams().values()) {
            team.setAllAlive();
        }

        // Title
        Bukkit.getServer().showTitle(new TitleBuilder()
            .title(Component.translatable("title.title.start"))
            .fade(0, 30, 10)
            .build()
        );

        // Update scoreboard
        board.setPlaceHolder("alive", alive.size() + "/" + players.size());
        board.setLines(plugin.getConfig().getStringList("scoreboard.playing"));
        gameLib.getOptions().setGameState(GameState.PLAYING);
        playing();
    }

    private void playing() {
        Bukkit.getServer().showBossBar(bossBar);
        Bukkit.getScheduler().runTask(plugin, new TimerRunnable(plugin, 20, 0, () -> {
            bossBar.name(Component.translatable("bossbar.invulnerability_gone"));
            Bukkit.getServer().playSound(Sound.sound(Key.key("invulnerability"), Sound.Source.MASTER, 1f, 1f));
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                player.setInvulnerable(false);
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> Bukkit.getServer().hideBossBar(bossBar), 100);
        }, (s, m) -> {
            if (s == 0) return;
            bossBar.name(Component.translatable("bossbar.invulnerability")
                    .append(Component.text(" " + s + " "))
                    .append(Component.translatable("bossbar.seconds"))
            );
        }));

        shrink();
    }

    private void shrink() {
        board.setLines(plugin.getConfig().getStringList("scoreboard.next_shrink"));
        TimerRunnable shrinkingTime = new TimerRunnable(plugin, options.getShrinkTime(), 0, () -> {
            if (shrinkStage <= 2) {
                shrink();
            } else {
                showdown();
            }
        }, (s, m) -> {
            board.setPlaceHolder("time", ChatManager.formatTimer(s, m));
            board.updateAll();
        });

        TimerRunnable shrinkingTask = new TimerRunnable(plugin, 30, 0, () -> {
            shrinkStage++;
            Bukkit.getServer().showTitle(
                    new TitleBuilder()
                            .title(Component.translatable("title.title.border_shrinking"))
                            .fade(5, 50, 5)
                            .build()
            );
            Bukkit.getServer().playSound(Sound.sound(Key.key("shrinking"), Sound.Source.MASTER, 1f, 1f));
            board.setLines(plugin.getConfig().getStringList("scoreboard.shrinking"));
            worldBorder.setSize(worldBorder.getSize() - options.getShrinkDistance(), options.getShrinkTime());
            Bukkit.getScheduler().runTask(plugin, shrinkingTime);
        }, (s, m) -> {
            board.setPlaceHolder("time", ChatManager.formatTimer(s, m));
            board.updateAll();
        });

        Bukkit.getScheduler().runTask(plugin, shrinkingTask);
    }

    private void showdown() {
        board.setLines(plugin.getConfig().getStringList("scoreboard.showdown"));

        Runnable showdownTask = new TimerRunnable(plugin, 0, 1, this::ending, (s, m) -> {
            board.setPlaceHolder("time", ChatManager.formatTimer(s, m));
            board.updateAll();
        });

        Bukkit.getScheduler().runTask(plugin, showdownTask);
    }

    private void ending() {
        gameLib.getOptions().setGameState(GameState.ENDING);
        for (UUID uuid : alive) {
            Player player = Bukkit.getPlayer(uuid);
            double winCoins = options.getWinCoins()[0] / alive.size();
            PlayerData playerData = DataUtil.getPlayerData(uuid);
            playerData.addLocalCoins(winCoins);
            player.sendMessage(Component.translatable("tellraw.win").args(Component.text(winCoins)));

            PlayerData.setSpectator(player);
            player.playSound(Sound.sound(Key.key("win"), Sound.Source.MASTER, 1f, 1f));
        }
        for (UUID uuid : players) {
            PlayerData playerData = DataUtil.getPlayerData(uuid);
            playerData.addCoins(playerData.getLocalCoins());
        }
        Bukkit.getScheduler().cancelTasks(plugin);
        board.setLines(plugin.getConfig().getStringList("scoreboard.ending"));
        ChatManager.showRoundLeaderboard(new ArrayList<>(DataUtil.getTeams().values()), players);
        Bukkit.getScheduler().runTask(plugin, new TimerRunnable(plugin, 0, 1, this::end, (s, m) -> {
            board.setPlaceHolder("time", ChatManager.formatTimer(s, m));
            board.updateAll();
        }));
    }

    private void checkWinners() {
        int aliveTeams = 0;
        for (Team team : DataUtil.getTeams().values()) {
            if (team.isAlive())
                aliveTeams++;
        }
        if (aliveTeams <= 1) {
            ending();
        }
    }

    private void end() {
        gameLib.getOptions().setGameState(GameState.LOBBY);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(Bukkit.getWorld("world").getSpawnLocation());
            PlayerData.setDefault(player);
            DataUtil.getPlayerData(player).resetLocalCoins();
        }
        level.unload();
        markers = null;
        players = null;
        alive = null;
        world = null;
        worldBorder = null;
        options = null;
        bossBar = null;
        gameLib.getServer().getPluginManager().disablePlugin(plugin);
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        board.update(player);
        GameState gameState = gameLib.getOptions().getGameState();
        if (gameState == GameState.PLAYING || gameState == GameState.STARTING || gameState == GameState.ENDING) {
            PlayerData.setSpectator(player);
            player.teleport(world.getSpawnLocation());
        }
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        DataUtil.getPlayerData(player).getTeam().getAliveUUID().remove(player.getUniqueId());
        players.remove(player.getUniqueId());
        alive.remove(player.getUniqueId());
        board.removeBoard(player);
        checkWinners();
    }

    @EventHandler
    private void onBlockDropItem(BlockDropItemEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        if (player.getGameMode() == GameMode.ADVENTURE)
            e.setCancelled(true);
    }

    @EventHandler
    private void onPlayerDeath(PlayerDeathEvent e) {
        e.deathMessage(null);
        Player killer = e.getEntity().getKiller();
        Player dead = e.getPlayer();
        PlayerData deadData = DataUtil.getPlayerData(dead);
        Team deadTeam = deadData.getTeam();
        int aliveTeams = (int) DataUtil.getTeams().values().stream().filter(Team::isAlive).count();
        alive.remove(dead.getUniqueId());
        deadTeam.getAliveUUID().remove(dead.getUniqueId());
        if (!deadData.getTeam().isAlive()) {
            Bukkit.getServer().sendMessage(Component.translatable("tellraw.team_eliminated").args(Component.text(deadTeam.getFormattedName())));
            double winCoins = options.getWinCoins()[aliveTeams - 1] / deadTeam.getBukkitPlayers().size();
            deadTeam.getBukkitPlayers().forEach(player -> {
                PlayerData playerData = DataUtil.getPlayerData(player);
                playerData.addLocalCoins(winCoins);
                player.sendMessage(Component.translatable("tellraw.win").args(Component.text(winCoins), Component.text(aliveTeams)));
            });
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            dead.spigot().respawn();
            PlayerData.setSpectator(dead);
        }, 40);

        dead.sendMessage(e.deathMessage());
        dead.playSound(Sound.sound(Key.key("death"), Sound.Source.MASTER, 1f, 1f));

        Component deathMsg = Component.translatable("tellraw.death").args(Component.text(options.getDeathCoins())).append(e.deathMessage());
        Sound deathSoundGlobal = Sound.sound(Key.key("coins.bonus"), Sound.Source.MASTER, 1f, 1f);
        alive.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            if (killer != null && player.getUniqueId().equals(killer.getUniqueId())) return;
            player.sendMessage(deathMsg);
            player.playSound(deathSoundGlobal);
            PlayerData pData = DataUtil.getPlayerData(player);
            pData.addLocalCoins(options.getDeathCoins());
            DataUtil.addPlayerData(uuid, pData);
        });

        board.setPlaceHolder("alive", alive.size() + "/" + players.size());
        board.updateAll();
        checkWinners();

        if (killer == null) return;
        PlayerData killerData = DataUtil.getPlayerData(killer);
        killerData.addLocalCoins(options.getKillCoins());
        killer.sendMessage(Component.translatable("tellraw.kill").args(Component.text(options.getKillCoins()), dead.teamDisplayName()));
        killer.playSound(Sound.sound(Key.key("coins.receive"), Sound.Source.MASTER, 1f, 1f));
        killer.showTitle(
                new TitleBuilder()
                    .subtitle(Component.translatable("title.subtitle.kill").append(dead.teamDisplayName()))
                    .fade(0, 40, 5)
                    .build()
        );
        world.spawnParticle(Particle.END_ROD, killer.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.1);
    }

    @EventHandler
    private void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (alive.contains(player.getUniqueId())) {
            if (!options.getAllowedBlockToBreak().contains(e.getBlock().getType())) {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (alive.contains(player.getUniqueId())) {
            if (!options.getAllowedBlockToPlace().contains(e.getBlock().getType())) {
                e.setCancelled(true);
            } else {
                Block block = e.getBlock();
               if (block.getType() == Material.TNT) {
                   block.setType(Material.AIR);
                   world.spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class, tntPrimed -> tntPrimed.setSource(player));
               }
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    private void onTntExplode(EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        e.setCancelled(true);
        if (entity instanceof TNTPrimed) {
            world.createExplosion(e.getLocation(), 1f, false, false, ((TNTPrimed) entity).getSource());
        }
    }
}
