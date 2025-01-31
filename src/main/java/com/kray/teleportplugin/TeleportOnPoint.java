package com.kray.teleportplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class TeleportOnPoint extends JavaPlugin {
    private final Map<UUID, List<Location>> playerTeleportPoints = new HashMap<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private int teleportInterval = 10; // Время между телепортациями
    private FileConfiguration messagesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessages();
        loadConfig();

        getServer().getScheduler().runTaskTimer(this, this::teleportActivePlayers, 0, teleportInterval * 20L);

        getCommand("tpreload").setExecutor(this);
        getCommand("tpadd").setExecutor(this);
        getCommand("tptoggle").setExecutor(this);
        getCommand("tptime").setExecutor(this);
        getCommand("tpclear").setExecutor(this);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        teleportInterval = config.getInt("tp_time-seconds", 10);
    }

    private void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String getMessage(String key) {
        return messagesConfig.getString(key, "§cСообщение отсутствует в messages.yml: " + key);
    }

    private void teleportActivePlayers() {
        for (UUID playerId : activePlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            List<Location> locations = playerTeleportPoints.get(playerId);
            if (locations == null || locations.isEmpty()) {
                player.sendMessage(getMessage("tp.error.no-points"));
                continue;
            }

            int index = (int) (System.currentTimeMillis() / (teleportInterval * 1000L)) % locations.size();
            player.teleport(locations.get(index));
            player.sendMessage(getMessage("tp.success"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error.player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage(getMessage("error.no-permission"));
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "tpreload":
                reloadConfig();
                loadMessages();
                loadConfig();
                player.sendMessage(getMessage("config.reload-success"));
                return true;

            case "tpadd":
                addTeleportPoint(player);
                return true;

            case "tptoggle":
                toggleTeleport(player);
                return true;

            case "tptime":
                if (args.length != 1) {
                    player.sendMessage(getMessage("tptime.usage"));
                    return true;
                }
                setTeleportInterval(player, args[0]);
                return true;

            case "tpclear":
                clearTeleportPoints(player);
                return true;
        }

        return false;
    }

    private void addTeleportPoint(Player player) {
        UUID playerId = player.getUniqueId();
        List<Location> locations = playerTeleportPoints.computeIfAbsent(playerId, k -> new ArrayList<>());

        Location loc = player.getLocation();
        locations.add(new Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));

        player.sendMessage(getMessage("tpadd.success"));
    }

    private void toggleTeleport(Player player) {
        UUID playerId = player.getUniqueId();
        if (activePlayers.contains(playerId)) {
            activePlayers.remove(playerId);
            player.sendMessage(getMessage("tptoggle.disabled"));
        } else {
            activePlayers.add(playerId);
            player.sendMessage(getMessage("tptoggle.enabled"));
        }
    }

    private void setTeleportInterval(Player player, String value) {
        try {
            int newInterval = Integer.parseInt(value);
            if (newInterval < 1) throw new NumberFormatException();
            teleportInterval = newInterval;
            getConfig().set("tp_time-seconds", teleportInterval);
            saveConfig();
            player.sendMessage(getMessage("tptime.success").replace("{time}", value));
        } catch (NumberFormatException e) {
            player.sendMessage(getMessage("tptime.invalid"));
        }
    }

    private void clearTeleportPoints(Player player) {
        UUID playerId = player.getUniqueId();
        playerTeleportPoints.remove(playerId);
        player.sendMessage(getMessage("tpclear.success"));
    }
}
