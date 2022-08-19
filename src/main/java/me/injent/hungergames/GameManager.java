package me.injent.hungergames;

import me.injent.gamelib.GameLib;
import me.injent.gamelib.board.Board;
import me.injent.gamelib.player.PlayerData;
import me.injent.gamelib.teams.SpawnLoc;
import me.injent.gamelib.teams.Team;
import me.injent.gamelib.util.ChatManager;
import me.injent.gamelib.util.TimerRunnable;
import me.injent.gamelib.util.TitleBuilder;
import me.injent.hungergames.util.MapOptions;
import me.injent.hungergames.util.WorldManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameManager implements Listener {

    private final HungerGames plugin;
    private final GameLib gameLib;

    private World world;
    private WorldBorder worldBorder;
    private BossBar bossBar;
    private MapOptions options = new MapOptions();

    private GameState gameState;
    private List<SpawnLoc> markers;
    private List<UUID> alive = new ArrayList<>();
    private List<UUID> players = new ArrayList<>();
    private Board board;
    private int shrinkStage;

    public GameManager(HungerGames plugin) {
        this.plugin = plugin;
        gameLib = plugin.getGameLib();

        // Loading data
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                markers = gameLib.getDataService().getLocations(plugin);
                options.prepopulate(plugin);
                gameState = GameState.WAITING;
                world = Bukkit.getWorld(options.getWorldName());
                worldBorder = world.getWorldBorder();
                bossBar = BossBar.bossBar(Component.text(""), 0.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                board = gameLib.getMainBoard();
                board.setPlaceHolder("game", options.getGame());
                board.setPlaceHolder("map", options.getMapName());
                options.setLoaded(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void waitingForStart() {
        // Checking conditions
        if (!options.isLoaded()) {
            plugin.getLogger().info("Game is loading!");
            return;
        }
        if (gameState == GameState.WAITING)
            gameState = GameState.PLAYING;
        else
            return;

        List<String> lines = plugin.getConfig().getStringList("scoreboard.waiting");
        lines.set(3, "Ожидание...");
        board.setLines(lines);

        starting();
    }

    private void starting() {
        // Teleporting player to their markers
        for (SpawnLoc spawnLoc : markers) {
            Team team = gameLib.getTeams().get(spawnLoc.getTeamId());
            if (team != null) {
                team.getBukkitPlayers().forEach(player -> player.teleport(spawnLoc.toLocation(world)));
            }
        }

        // Preparing for game
        for (UUID uuid : gameLib.getPlayersData().keySet()) {
            if (Bukkit.getPlayer(uuid) != null)
                players.add(uuid);
        }
        alive = new ArrayList<>(players);

        // Runt countdown timer
        gameState = GameState.STARTING;
        worldBorder.setSize(options.getBorderSize());

        board.setLines(plugin.getConfig().getStringList("scoreboard.starting"));
        Bukkit.getScheduler().runTask(plugin, new TimerRunnable(plugin, 10, 0, this::start, (s, m) -> {
            if (s == 0 && m == 0) return;
            Bukkit.getServer().showTitle(new TitleBuilder().title(Component.translatable("title.title.starting"))
                .subtitle(Component.text("> " + s + " <"))
                .fade(0, 30, 5)
                .build()
            );

            String time = ChatManager.formatTimer(s, m);
            board.setLine(3, time);

            if (s <= 5 && s != 0) {
                Bukkit.getServer().playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_COW_BELL, Sound.Source.MASTER, 1, 3.5f));
            }
        }));
    }

    private void start() {
        plugin.getLogger().info("Game started!");
        // Remove barriers
        for (SpawnLoc spawnLoc : markers) {
            Location loc = spawnLoc.toLocation(world);
            WorldManager.fillBlocks(loc.clone().add(-2.0,0.0,-2.0), loc.clone().add(2.0,3.0,2.0), Material.AIR);
        }

        // Player set data
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            PlayerData.setDefault(player);
        }

        for (Team team : gameLib.getTeams().values()) {
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
        playing();
    }

    private void playing() {
        Bukkit.getServer().showBossBar(bossBar);
        Bukkit.getScheduler().runTask(plugin, new TimerRunnable(plugin, 20, 0, () -> {
            bossBar.name(Component.translatable("bossbar.invulnerability_gone"));
            Bukkit.getServer().playSound(Sound.sound(Key.key("block.respawn_anchor.deplete"), Sound.Source.MASTER, 1f, 1f));
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
            String time = ChatManager.formatTimer(s, m);
            board.setLine(3, time);
        });

        TimerRunnable shrinkingTask = new TimerRunnable(plugin, 30, 0, () -> {
            shrinkStage++;
            Bukkit.getServer().showTitle(
                    new TitleBuilder()
                            .title(Component.translatable("title.title.border_shrinking"))
                            .fade(5, 50, 5)
                            .build()
            );
            Bukkit.getServer().playSound(Sound.sound(Key.key("block.respawn_anchor.charge"), Sound.Source.MASTER, 1f, 1f));
            board.setLines(plugin.getConfig().getStringList("scoreboard.shrinking"));
            worldBorder.setSize(worldBorder.getSize() - options.getShrinkDistance(), options.getShrinkTime());
            Bukkit.getScheduler().runTask(plugin, shrinkingTime);
        }, (s, m) -> {
            String time = ChatManager.formatTimer(s, m);
            board.setLine(3, time);
        });

        Bukkit.getScheduler().runTask(plugin, shrinkingTask);
    }

    private void showdown() {
        board.setLines(plugin.getConfig().getStringList("scoreboard.showdown"));

        Runnable showdownTask = new TimerRunnable(plugin, 0, 1, this::ending, (s, m) -> {
            String time = ChatManager.formatTimer(s, m);
            board.setLine(3, time);
        });

        Bukkit.getScheduler().runTask(plugin, showdownTask);
    }

    private void ending() {
        gameState = GameState.ENDING;
        for (UUID uuid : alive) {
            Player player = Bukkit.getPlayer(uuid);
            player.setGameMode(GameMode.SPECTATOR);
            PlayerData playerData = gameLib.getPlayersData().get(uuid);
            player.sendMessage(Component.translatable("tellraw.win").args(Component.text(options.getWinCoins())));
            playerData.addLocalCoins(options.getWinCoins());
        }
        Bukkit.getScheduler().cancelTasks(plugin);
        board.setLines(plugin.getConfig().getStringList("scoreboard.ending"));
        ChatManager.showRoundLeaderboard(new ArrayList<>(gameLib.getTeams().values()), players);
        Bukkit.getScheduler().runTask(plugin, new TimerRunnable(plugin, 0, 1, this::end, (s, m) -> {
            String time = ChatManager.formatTimer(s, m);
            board.setLine(3, time);
        }));
    }

    private void checkWinners() {
        int aliveTeams = 0;
        for (Team team : gameLib.getTeams().values()) {
            if (team.isAlive())
                aliveTeams++;
        }
        if (aliveTeams == 1) {
            ending();
        } else if (aliveTeams == 0) {
            plugin.getLogger().severe("STOPPING THE SERVER!");
        }
    }

    enum GameState {
        WAITING,
        STARTING,
        PLAYING,
        ENDING,
        END
    }

    private void end() {
        gameState = GameState.END;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        board.update(player);
        if (gameState == GameState.PLAYING) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        PlayerData playerData = gameLib.getPlayersData().get(player.getUniqueId());
        playerData.getTeam().getAliveUUID().remove(player.getUniqueId());
        players.remove(player.getUniqueId());
        alive.remove(player.getUniqueId());
        board.removeBoard(player);
        checkWinners();
    }

    @EventHandler
    public void onPlayerDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Player dead)) return;
        PlayerData playerData = gameLib.getPlayersData().get(dead.getUniqueId());
        playerData.setPlace(alive.size());
        alive.remove(dead.getUniqueId());
        playerData.getTeam().getAliveUUID().remove(dead.getUniqueId());

        e.setCancelled(true);
        dead.setGameMode(GameMode.SPECTATOR);

        alive.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            player.sendMessage(Component.translatable("tellraw.death").args(Component.text(options.getDeathCoins()), player.displayName()));
            player.playSound(Sound.sound(Key.key("block.chain.place"), Sound.Source.MASTER, 1f, 1.5f));
            PlayerData pData = gameLib.getPlayersData().get(player.getUniqueId());
            pData.addCoins(options.getDeathCoins());
        });

        board.setPlaceHolder("alive", alive.size() + "/" + players.size());
        board.updateAll();
        checkWinners();

        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        PlayerData killerData = gameLib.getPlayersData().get(killer.getUniqueId());
        killerData.addCoins(options.getKillCoins());
        killer.sendMessage(Component.translatable("tellraw.kill").args(Component.text(options.getKillCoins()), dead.displayName()));
        killer.playSound(Sound.sound(Key.key("block.chain.place"), Sound.Source.MASTER, 1f, 1.5f));
        world.spawnParticle(Particle.END_ROD, killer.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.1);
    }
}
