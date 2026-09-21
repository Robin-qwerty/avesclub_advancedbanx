package net.hnt8.advancedban.backendlink;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BackendLinkMain extends JavaPlugin {
    
    private static BackendLinkMain instance;
    public static final String CHANNEL = "advancedban:command";
    private static final List<String> COMMANDS = Arrays.asList(
        "ban", "tempban", "punish", "banip", "tempipban",
        "mute", "tempmute", "warn", "tempwarn", "kick",
        "unban", "unmute"
    );
    private BackendPunishmentListener punishmentListener;
    private String linkName = "survival";

    @Override
    public void onLoad() {
        instance = this;
        BackendLinkConfigFiles.ensure(this);
    }

    @Override
    public void onEnable() {
        instance = this;
        BackendLinkConfigFiles.ensure(this);
        reloadConfig();
        linkName = BackendLinkSettings.linkName(getConfig());

        // Check if the full Avesban plugin is loaded
        Plugin fullPlugin = Bukkit.getPluginManager().getPlugin("Avesban");
        if (fullPlugin != null && fullPlugin.isEnabled()) {
            getLogger().warning("Avesban full plugin is already loaded! BackendLink is not needed.");
            getLogger().warning("Disabling BackendLink to avoid conflicts...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Register plugin messaging channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        punishmentListener = new BackendPunishmentListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, punishmentListener);
        getServer().getPluginManager().registerEvents(punishmentListener, this);
        
        // Register all commands
        for (String cmd : COMMANDS) {
            PluginCommand command = getCommand(cmd);
            if (command != null) {
                command.setExecutor(new BackendCommandExecutor(cmd));
            } else {
                getLogger().warning("Failed to register command: " + cmd);
            }
        }

        BackendLinkRedis.start(this, BackendLinkSettings.redis(getConfig()), linkName);
        
        getLogger().info("Avesban BackendLink enabled as '" + linkName + "'. Commands will be forwarded to Velocity.");

        // Keep mute state fairly fresh for online players (Folia: global + per-entity region).
        BackendLinkScheduler.scheduleRepeating(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                BackendLinkScheduler.runOnPlayerRegion(this, player, () -> requestPunishmentStatus(player), 0L);
            }
        }, 40L, 100L);
    }

    @Override
    public void onDisable() {
        BackendLinkRedis.shutdown();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getLogger().info("Avesban BackendLink disabled!");
    }

    public static BackendLinkMain getInstance() {
        return instance;
    }

    /**
     * Sends a command to the Velocity proxy via Redis when enabled, otherwise plugin messaging.
     */
    public void sendCommandToProxy(String command, String[] args, String operator) {
        StringBuilder fullCommand = new StringBuilder(command);
        for (String arg : args) {
            fullCommand.append(" ").append(arg);
        }

        try {
            if (BackendLinkRedis.publishCommand(command, args, operator, resolveServerName())) {
                getLogger().fine("Sent command to proxy via Redis: " + fullCommand);
                return;
            }
        } catch (Exception ex) {
            getLogger().warning("Redis command publish failed, falling back to plugin messaging: " + ex.getMessage());
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            getLogger().warning("Cannot send command to proxy: Redis is disabled and no players are online to relay a plugin message.");
            getLogger().warning("Command that failed: " + fullCommand.toString());
            return;
        }
        
        org.bukkit.entity.Player relayPlayer = Bukkit.getOnlinePlayers().iterator().next();
        
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        
        try {
            String serverName = resolveServerName();
            
            out.writeUTF("EXECUTE_COMMAND");
            out.writeUTF(fullCommand.toString());
            out.writeUTF(serverName);
            
            relayPlayer.sendPluginMessage(this, CHANNEL, byteOut.toByteArray());
            
            getLogger().fine("Sent command to proxy: " + fullCommand.toString() + " from server: " + serverName);
        } catch (IOException e) {
            getLogger().severe("Failed to send plugin message to proxy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendCommandToProxy(String command, String[] args) {
        sendCommandToProxy(command, args, "CONSOLE");
    }

    public void requestPunishmentStatus(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOut);
            out.writeUTF("CHECK_PUNISHMENT");
            out.writeUTF(player.getName());
            out.writeUTF(player.getUniqueId().toString().replace("-", ""));
            out.writeUTF(resolveServerName());
            player.sendPluginMessage(this, CHANNEL, byteOut.toByteArray());
        } catch (IOException ex) {
            getLogger().warning("Failed to request punishment status for " + player.getName() + ": " + ex.getMessage());
        }
    }

    private String resolveServerName() {
        if (linkName != null && !linkName.isEmpty()) {
            return linkName;
        }
        String serverName = Bukkit.getServer().getName();
        if (serverName == null || serverName.isEmpty()) {
            serverName = System.getProperty("server.name");
        }
        if (serverName == null || serverName.isEmpty()) {
            serverName = System.getenv("SERVER_NAME");
        }
        if (serverName == null || serverName.isEmpty()) {
            String address = Bukkit.getServer().getIp();
            int port = Bukkit.getServer().getPort();
            serverName = (address != null && !address.isEmpty()) ? address + ":" + port : "backend-server";
        }
        return serverName;
    }
}
