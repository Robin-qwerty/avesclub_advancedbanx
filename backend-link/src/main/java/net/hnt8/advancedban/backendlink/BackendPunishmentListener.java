package net.hnt8.advancedban.backendlink;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a lightweight local mute cache and enforces chat mutes on backend servers.
 */
public class BackendPunishmentListener implements Listener, PluginMessageListener {

    private final BackendLinkMain plugin;
    private final Map<String, Boolean> mutedByName = new ConcurrentHashMap<>();

    public BackendPunishmentListener(BackendLinkMain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Delay one tick so plugin messaging is fully available for this connection.
        BackendLinkScheduler.runOnPlayerRegion(plugin, event.getPlayer(),
                () -> plugin.requestPunishmentStatus(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mutedByName.remove(event.getPlayer().getName().toLowerCase());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String key = event.getPlayer().getName().toLowerCase();
        if (Boolean.TRUE.equals(mutedByName.get(key))) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            BackendLinkScheduler.runOnPlayerRegion(plugin, p,
                    () -> p.sendMessage("§cYou are muted and cannot chat right now."), 0L);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BackendLinkMain.CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String type = in.readUTF();
            if (!"PUNISHMENT_STATUS".equals(type)) {
                return;
            }

            String playerName = in.readUTF();
            boolean muted = in.readBoolean();
            mutedByName.put(playerName.toLowerCase(), muted);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to read punishment status message: " + ex.getMessage());
        }
    }
}

