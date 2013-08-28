package me.libraryaddict.Hungergames;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import me.libraryaddict.Hungergames.Events.FeastAnnouncedEvent;
import me.libraryaddict.Hungergames.Events.FeastSpawnedEvent;
import me.libraryaddict.Hungergames.Events.GameStartEvent;
import me.libraryaddict.Hungergames.Events.InvincibilityWearOffEvent;
import me.libraryaddict.Hungergames.Events.PlayerWinEvent;
import me.libraryaddict.Hungergames.Events.ServerShutdownEvent;
import me.libraryaddict.Hungergames.Events.TimeSecondEvent;
import me.libraryaddict.Hungergames.Listeners.GeneralListener;
import me.libraryaddict.Hungergames.Listeners.PlayerListener;
import me.libraryaddict.Hungergames.Managers.ConfigManager;
import me.libraryaddict.Hungergames.Managers.LibsFeastManager;
import me.libraryaddict.Hungergames.Managers.MySqlManager;
import me.libraryaddict.Hungergames.Managers.PlayerManager;
import me.libraryaddict.Hungergames.Managers.ScoreboardManager;
import me.libraryaddict.Hungergames.Managers.TranslationManager;
import me.libraryaddict.Hungergames.Types.Gamer;
import me.libraryaddict.Hungergames.Types.HungergamesApi;
import me.libraryaddict.Hungergames.Types.Kit;
import me.libraryaddict.Hungergames.Utilities.MapLoader;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;

public class Hungergames extends JavaPlugin {
    private class BlockInfo {
        int x;
        int z;
    }

    public boolean chunksGenerating = true;
    private TranslationManager cm;
    private ConfigManager config;
    /**
     * This plugin is licensed under http://creativecommons.org/licenses/by-nc/3.0/ Namely. No code may be taken from this for
     * commercial use and the plugin may not be adapted for commercial use. Keep the /creator command in, leave my name in as the
     * author. Do not attempt to change the author, such as 'Notch made this plugin specially for hungergames.com!' No seriously.
     * I had idiots approaching me for a previous plugin "How do I remove your name and add mine instead?" This is something I've
     * invested time, effort and knowledge in. Creator being: libraryaddict
     */
    public int currentTime = -270;
    /**
     * doSeconds is false when the game has ended
     */
    public boolean doSeconds = true;
    public HashMap<Location, EntityType> entitys = new HashMap<Location, EntityType>();
    public Location feastLoc;
    private Metrics metrics;
    private PlayerListener playerListener;
    private PlayerManager pm;
    public World world;

    public void cannon() {
        world.playSound(world.getSpawnLocation(), Sound.AMBIENCE_THUNDER, 10000, 2.9F);
    }

    public void checkWinner() {
        if (doSeconds) {
            List<Gamer> aliveGamers = pm.getAliveGamers();
            if (aliveGamers.size() == 1) {
                doSeconds = false;
                final Gamer winner = aliveGamers.get(0);
                Bukkit.getPluginManager().callEvent(new PlayerWinEvent(winner));
                int reward = getPrize(1);
                if (reward > 0)
                    winner.addBalance(reward);
                winner.getPlayer().setAllowFlight(true);
                Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                    public void run() {
                        Bukkit.broadcastMessage(String.format(cm.getBroadcastWinnerWon(), winner.getName()));
                    }
                }, 0, config.getWinnerBroadcastDelay() * 20);
                Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                    public void run() {
                        String kick = String.format(cm.getKickMessageWon(), winner.getName());
                        for (Player p : Bukkit.getOnlinePlayers())
                            p.kickPlayer(kick);
                        shutdown();
                    }
                }, config.getGameShutdownDelay() * 20);
            } else if (aliveGamers.size() == 0) {
                doSeconds = false;
                for (Player p : Bukkit.getOnlinePlayers())
                    p.kickPlayer(cm.getKickNobodyWonMessage());
                shutdown();
            }
        }
    }

    private void doBorder(Gamer gamer) {
        Player p = gamer.getPlayer();
        Location loc = p.getLocation().clone();
        Location sLoc = world.getSpawnLocation().clone();
        final double border = config.getBorderSize();
        if (config.isRoundedBorder()) {
            sLoc.setY(loc.getY());
            double fromBorder = loc.distance(sLoc) - border;
            if (fromBorder - 20 > 0) {
                // Warn
                p.sendMessage(cm.getMessagePlayerApproachingBorder());
                if (fromBorder > 0) {
                    // Punish
                    if (gamer.isAlive()) {
                        // Damage and potentially kill.
                        if (p.getHealth() - 2 > 0) {
                            p.damage(0);
                            p.setHealth(p.getHealth() - 2);
                        } else {
                            pm.killPlayer(gamer, null, loc, gamer.getInventory(),
                                    String.format(cm.getKillMessageKilledByBorder(), gamer.getName()));
                        }
                    } else if (border > 10) {
                        // Hmm. Preferably I tp them back inside.
                        // May as well just tp to spawn. No harm done.
                        pm.sendToSpawn(gamer);
                    }
                }
            }
        } else {
            Location tpTo = loc.clone();
            int fromSpawn = loc.getBlockX() - sLoc.getBlockX();
            if (fromSpawn > border - 20) {
                tpTo.setX(((border - 2) + sLoc.getBlockX()));
            }
            if (fromSpawn < -(border - 20)) {
                tpTo.setX((-(border - 2) + sLoc.getBlockX()));
            }
            boolean hurt = Math.abs(fromSpawn) >= border;
            fromSpawn = loc.getBlockZ() - sLoc.getBlockZ();
            if (fromSpawn > (border - 20)) {
                tpTo.setZ(((border - 2) + sLoc.getBlockZ()));
            }
            if (fromSpawn < -(border - 20)) {
                tpTo.setZ((-(border - 2) + sLoc.getBlockZ()));
            }
            if (!hurt)
                hurt = Math.abs(fromSpawn) >= border;
            if (!loc.equals(tpTo))
                p.sendMessage(cm.getMessagePlayerApproachingBorder());
            if (hurt) {
                if (gamer.isAlive()) {
                    // Damage and potentially kill.
                    if (p.getHealth() - 2 > 0) {
                        p.damage(0);
                        p.setHealth(p.getHealth() - 2);
                    } else {
                        pm.killPlayer(gamer, null, loc, gamer.getInventory(),
                                String.format(cm.getKillMessageKilledByBorder(), gamer.getName()));
                    }
                } else if (border > 10)
                    gamer.getPlayer().teleport(tpTo);
            }
        }
    }

    private void generateChunks() {
        final Location spawn = world.getSpawnLocation();
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++)
                spawn.clone().add(x * 16, 0, z * 16).getChunk().load();
        File mapConfig = new File(getDataFolder() + "/map.yml");
        YamlConfiguration mapConfiguration = YamlConfiguration.loadConfiguration(mapConfig);
        if (mapConfiguration.getBoolean("GenerateChunks")) {
            final double chunks = (int) Math.ceil(config.getBorderSize() / 16) + Bukkit.getViewDistance();
            final ArrayList<BlockInfo> toProcess = new ArrayList<BlockInfo>();
            for (int x = (int) -chunks; x <= chunks; x++) {
                for (int z = (int) -chunks; z <= chunks; z++) {
                    BlockInfo info = new BlockInfo();
                    info.x = spawn.getBlockX() + (x * 16);
                    info.z = spawn.getBlockZ() + (z * 16);
                    toProcess.add(info);
                }
            }
            final double totalChunks = toProcess.size();
            final boolean background = mapConfiguration.getBoolean("GenerateChunksBackground");
            if (background)
                chunksGenerating = false;
            BukkitRunnable runnable = new BukkitRunnable() {
                int currentChunks = 0;
                long lastPrint = 0;

                public void run() {
                    if (lastPrint + 5000 < System.currentTimeMillis()) {
                        System.out.print(String.format(cm.getLoggerGeneratingChunks(),
                                (int) Math.floor(((double) currentChunks / totalChunks) * 100))
                                + "%");
                        lastPrint = System.currentTimeMillis();
                    }
                    Iterator<BlockInfo> itel = toProcess.iterator();
                    long started = System.currentTimeMillis();

                    while (itel.hasNext() && started + (background ? 50 : 5000) > System.currentTimeMillis()) {
                        currentChunks++;
                        BlockInfo info = itel.next();
                        itel.remove();
                        Chunk chunk = world.getChunkAt(info.x, info.z);
                        if (chunk.isLoaded())
                            continue;
                        chunk.load();
                        chunk.unload(true, false);
                    }
                    if (!itel.hasNext()) {
                        chunksGenerating = false;
                        System.out.print(String.format(cm.getLoggerChunksGenerated(), currentChunks));
                        cancel();
                    }
                }
            };
            runnable.runTaskTimer(HungergamesApi.getHungergames(), 1, 5);
        } else
            chunksGenerating = false;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public int getPrize(int pos) {
        if (getConfig().contains("Winner" + pos))
            return getConfig().getInt("Winner" + pos, 0);
        return 0;
    }

    public boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kickPlayer(cm.getKickGameShutdownUnexpected());
            PlayerQuitEvent event = new PlayerQuitEvent(p, "He came, he saw, he conquered");
            playerListener.onQuit(event);
        }
        HungergamesApi.getMySqlManager().getPlayerJoinThread().mySqlDisconnect();
        HungergamesApi.getMySqlManager().getPlayerJoinThread().stop();
    }

    public void onEnable() {
        HungergamesApi.init(this);
        cm = HungergamesApi.getTranslationManager();
        pm = HungergamesApi.getPlayerManager();
        config = HungergamesApi.getConfigManager();
        MySqlManager mysql = HungergamesApi.getMySqlManager();
        mysql.SQL_DATA = getConfig().getString("MySqlDatabase");
        mysql.SQL_HOST = getConfig().getString("MySqlUrl");
        mysql.SQL_PASS = getConfig().getString("MySqlPass");
        mysql.SQL_USER = getConfig().getString("MySqlUser");
        mysql.startJoinThread();
        MapLoader.loadMap();
        try {
            metrics = new Metrics(this);
            if (metrics.isOptOut())
                System.out.print(cm.getLoggerMetricsMessage());
            metrics.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                world = Bukkit.getWorlds().get(0);
                world.setGameRuleValue("doDaylightCycle", "false");
                world.setTime(6000);
                if (config.forceCords())
                    world.setSpawnLocation(config.getSpawnX(), world.getHighestBlockYAt(config.getSpawnX(), config.getSpawnZ()),
                            config.getSpawnZ());
                final Location spawn = world.getSpawnLocation();
                for (int x = -5; x <= 5; x++)
                    for (int z = -5; z <= 5; z++)
                        spawn.clone().add(x * 16, 0, z * 16).getChunk().load();
                File mapConfig = new File(getDataFolder() + "/map.yml");
                YamlConfiguration mapConfiguration = YamlConfiguration.loadConfiguration(mapConfig);
                generateChunks();
                if (mapConfiguration.getBoolean("GenerateSpawnPlatform")) {
                    ItemStack spawnGround = config.parseItem(mapConfiguration.getString("SpawnPlatformIDandData"));
                    LibsFeastManager feastManager;
                    if (HungergamesApi.getFeastManager() instanceof LibsFeastManager)
                        feastManager = (LibsFeastManager) HungergamesApi.getFeastManager();
                    else
                        feastManager = new LibsFeastManager();
                    int platformHeight = HungergamesApi.getFeastManager().getSpawnHeight(world.getSpawnLocation(),
                            mapConfiguration.getInt("SpawnPlatformSize"));
                    feastManager.generatePlatform(world.getSpawnLocation(), platformHeight,
                            mapConfiguration.getInt("SpawnPlatformSize"), 100, spawnGround.getTypeId(),
                            spawnGround.getDurability());
                    world.getSpawnLocation().setY(platformHeight + 2);
                }
                world.setDifficulty(Difficulty.HARD);
                if (world.hasStorm())
                    world.setStorm(false);
                world.setWeatherDuration(999999999);
                feastLoc = new Location(world, spawn.getX() + (new Random().nextInt(200) - 100), 0, spawn.getZ()
                        + (new Random().nextInt(200) - 100));
                ScoreboardManager.updateStage();
                HungergamesApi.getFeastManager();
            }
        });
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            private long time = 0;

            public void run() {
                if (System.currentTimeMillis() >= time && doSeconds) {
                    time = System.currentTimeMillis() + 1000;
                    onSecond();
                    Bukkit.getPluginManager().callEvent(new TimeSecondEvent());
                }
            }
        }, 2L, 1L);
        HungergamesApi.getCommandManager();
        playerListener = new PlayerListener();
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        Bukkit.getPluginManager().registerEvents(new GeneralListener(), this);
        HungergamesApi.getAbilityManager();
        HungergamesApi.getInventoryManager().updateSpectatorHeads();
        if (Bukkit.getPluginManager().getPermission("ThisIsUsedForMessaging") == null) {
            Permission perm = new Permission("ThisIsUsedForMessaging", PermissionDefault.TRUE);
            perm.setDescription("Used for messages in LibsHungergames");
            Bukkit.getPluginManager().addPermission(perm);
        }
    }

    private void onSecond() {
        currentTime++;
        for (Gamer gamer : pm.getGamers()) {
            this.doBorder(gamer);
        }
        if (currentTime < 0) {
            world.setTime(0);
            ScoreboardManager.makeScore("Main", DisplaySlot.SIDEBAR, cm.getScoreBoardGameStartingIn(), -currentTime);
            if (config.displayMessages())
                if (config.advertiseGameStarting(currentTime))
                    Bukkit.broadcastMessage(String.format(cm.getBroadcastGameStartingIn(), returnTime(currentTime)));
        } else if (currentTime == 0) {
            if (pm.getGamers().size() < config.getMinPlayers()) {
                currentTime = -90;
                Bukkit.broadcastMessage(cm.getBroadcastNotEnoughPlayers());
                return;
            }
            startGame();
            return;
        } else if (currentTime == config.getTimeFeastStarts()) {
            ScoreboardManager.hideScore("Main", DisplaySlot.SIDEBAR, cm.getScoreboardFeastStartingIn());
            HungergamesApi.getFeastManager().generateChests(feastLoc, config.getChestLayers());
            Bukkit.broadcastMessage(cm.getBroadcastFeastBegun());
            ScoreboardManager.updateStage();
            world.playSound(world.getSpawnLocation(), Sound.IRONGOLEM_DEATH, 1000, 0);
            Bukkit.getPluginManager().callEvent(new FeastSpawnedEvent());
        } else if (config.feastStartsIn() > 0 && config.feastStartsIn() <= (5 * 60)) {
            ScoreboardManager.makeScore("Main", DisplaySlot.SIDEBAR, cm.getScoreboardFeastStartingIn(), config.feastStartsIn());
            if (config.advertiseFeast(currentTime)) {
                if (feastLoc.getBlockY() == 0) {
                    feastLoc.setY(world.getHighestBlockYAt(feastLoc.getBlockX(), feastLoc.getBlockZ()));
                    int feastHeight = HungergamesApi.getFeastManager().getSpawnHeight(feastLoc, config.getFeastSize());
                    HungergamesApi.getFeastManager().generatePlatform(feastLoc, feastHeight, config.getFeastSize());
                    ScoreboardManager.updateStage();
                    HungergamesApi.getInventoryManager().updateSpectatorHeads();
                    Bukkit.getPluginManager().callEvent(new FeastAnnouncedEvent());
                }
                Bukkit.broadcastMessage(String.format(cm.getBroadcastFeastStartingIn(), feastLoc.getBlockX(),
                        feastLoc.getBlockY(), feastLoc.getBlockZ(), returnTime(config.feastStartsIn()))
                        + (config.feastStartsIn() > 10 ? cm.getBroadcastFeastStartingCompassMessage() : ""));
            }
        } else if (config.getBorderCloseInRate() > 0 && currentTime > config.getTimeFeastStarts()) {
            config.setBorderSize(config.getBorderSize() - config.getBorderCloseInRate());
            ScoreboardManager.makeScore("Main", DisplaySlot.SIDEBAR, cm.getScoreboardBorderSize(), (int) config.getBorderSize());
        }
        if (currentTime > config.getTimeFeastStarts() + (5 * 60))
            ScoreboardManager.updateStage();
        if (config.getInvincibilityTime() > 0 && currentTime <= config.getInvincibilityTime() && currentTime >= 0) {
            ScoreboardManager.makeScore("Main", DisplaySlot.SIDEBAR, cm.getScoreboardInvincibleRemaining(),
                    config.invincibilityWearsOffIn());
            if (currentTime == config.getInvincibilityTime()) {
                Bukkit.broadcastMessage(cm.getBroadcastInvincibilityWornOff());
                ScoreboardManager.updateStage();
                ScoreboardManager.hideScore("Main", DisplaySlot.SIDEBAR, cm.getScoreboardInvincibleRemaining());
                Bukkit.getPluginManager().callEvent(new InvincibilityWearOffEvent());
            } else if (config.displayMessages() && config.advertiseInvincibility(currentTime)) {
                Bukkit.broadcastMessage(String.format(cm.getBroadcastInvincibiltyWearsOffIn(),
                        returnTime(config.invincibilityWearsOffIn())));
            }

        }
    }

    public String returnTime(Integer i) {
        i = Math.abs(i);
        int remainder = i % 3600, minutes = remainder / 60, seconds = remainder % 60;
        if (seconds == 0 && minutes == 0)
            return cm.getTimeFormatNoTime();
        if (minutes == 0) {
            if (seconds == 1)
                return String.format(cm.getTimeFormatSecond(), seconds);
            return String.format(cm.getTimeFormatSeconds(), seconds);
        }
        if (seconds == 0) {
            if (minutes == 1)
                return String.format(cm.getTimeFormatMinute(), minutes);
            return String.format(cm.getTimeFormatMinutes(), minutes);
        }
        if (seconds == 1) {
            if (minutes == 1)
                return String.format(cm.getTimeFormatSecondAndMinute(), minutes, seconds);
            return String.format(cm.getTimeFormatSecondAndMinutes(), minutes, seconds);
        }
        if (minutes == 1) {
            return String.format(cm.getTimeFormatSecondsAndMinute(), minutes, seconds);
        }
        return String.format(cm.getTimeFormatSecondsAndMinutes(), minutes, seconds);
    }

    public void shutdown() {
        System.out.print(cm.getLoggerShuttingDown());
        ServerShutdownEvent event = new ServerShutdownEvent();
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            for (String command : config.getCommandsToRunBeforeShutdown())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), getConfig().getString("StopServerCommand"));
        } else
            System.out.print(cm.getLoggerShutdownCancelled());
    }

    public void startGame() {
        currentTime = 0;
        for (Kit kit : HungergamesApi.getKitManager().getKits()) {
            final int amount = kit.getPlayerSize();
            if (amount <= 0)
                continue;
            metrics.getKitsUsed().addPlotter(new Metrics.Plotter(kit.getName()) {

                @Override
                public int getValue() {
                    return amount;
                }

            });
        }
        ScoreboardManager.updateStage();
        ScoreboardManager.hideScore("Main", DisplaySlot.SIDEBAR, cm.getScoreBoardGameStartingIn());
        ScoreboardManager.makeScore("Main", DisplaySlot.PLAYER_LIST, "", 0);
        if (config.getInvincibilityTime() > 0)
            ScoreboardManager.makeScore("Main", DisplaySlot.SIDEBAR, cm.getScoreboardInvincibleRemaining(),
                    config.getInvincibilityTime());
        else
            Bukkit.getPluginManager().callEvent(new InvincibilityWearOffEvent());
        Bukkit.broadcastMessage(cm.getBroadcastGameStartedMessage());
        if (config.getInvincibilityTime() > 0 && config.displayMessages())
            Bukkit.broadcastMessage(String.format(cm.getBroadcastInvincibiltyWearsOffIn(),
                    returnTime(config.getInvincibilityTime())));
        for (Gamer gamer : pm.getGamers()) {
            if (config.useKitSelector())
                gamer.getPlayer().getInventory().remove(HungergamesApi.getInventoryManager().getKitSelector());
            gamer.seeInvis(false);
            gamer.setAlive(true);
            pm.sendToSpawn(gamer);
        }
        for (Gamer gamer : pm.getGamers())
            gamer.updateSelfToOthers();
        world.setGameRuleValue("doDaylightCycle", "true");
        world.setTime(config.getTimeOfDay());
        world.playSound(world.getSpawnLocation(), Sound.AMBIENCE_THUNDER, 1, 0.8F);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                for (Gamer gamer : pm.getAliveGamers())
                    gamer.getPlayer().getInventory().addItem(new ItemStack(Material.COMPASS));
                for (me.libraryaddict.Hungergames.Types.Kit kit : HungergamesApi.getKitManager().getKits())
                    kit.giveKit();
                HungergamesApi.getAbilityManager().registerAbilityListeners();
                Bukkit.getPluginManager().callEvent(new GameStartEvent());
                for (Location l : entitys.keySet())
                    l.getWorld().spawnEntity(l, entitys.get(l));
                entitys.clear();
            }
        });
        checkWinner();
        HungergamesApi.getInventoryManager().updateSpectatorHeads();
    }
}
