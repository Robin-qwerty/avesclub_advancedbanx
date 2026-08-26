package net.hnt8.advancedban.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.manager.UUIDManager;
import net.hnt8.advancedban.utils.Punishment;
import net.hnt8.advancedban.velocity.BedrockCompat;

/**
 * Handles server connection events for Velocity to check server-specific bans
 */
public class ServerConnectListener {

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        if (!event.getResult().isAllowed()) {
            return; // Connection already denied
        }
        
        String serverName = event.getOriginalServer().getServerInfo().getName();
        String uuid = UUIDManager.get().getUUID(player.getUsername().toLowerCase());
        if (uuid == null) {
            uuid = player.getUniqueId().toString().replace("-", "");
        }
        
        // Check for server-specific bans
        // Check both network-wide and server-specific bans
        Punishment ban = PunishmentManager.get().getBan(uuid, serverName);
        
        if (ban != null) {
            // Check if this ban applies to this server
            if (ban.getTargetServer() == null) {
                // Network-wide ban - block on all servers
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(BedrockCompat.disconnectReason(player, ban.getLayoutBSN()));
            } else if (ban.getTargetServer().equalsIgnoreCase(serverName)) {
                // Server-specific ban for this server - block
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(BedrockCompat.disconnectReason(player, ban.getLayoutBSN()));
            }
            // If ban is for a different server, allow connection
        }
    }
}

