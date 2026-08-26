package net.hnt8.advancedban.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.manager.UUIDManager;
import net.hnt8.advancedban.velocity.BedrockCompat;

/**
 * Handles player connections and disconnections for Velocity
 */
public class ConnectionListenerVelocity {

    @Subscribe
    public EventTask onConnection(LoginEvent event) {
        Player player = event.getPlayer();
        UUIDManager.get().supplyInternUUID(player.getUsername(), player.getUniqueId());

        return EventTask.async(() -> {
            String result = Universal.get().callConnection(player.getUsername(),
                player.getRemoteAddress().getAddress().getHostAddress(), null, player);

            if (result != null) {
                event.setResult(LoginEvent.ComponentResult.denied(BedrockCompat.disconnectReason(player, result)));
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Universal.get().getMethods().runAsync(() -> {
            Player player = event.getPlayer();
            if (player != null) {
                PunishmentManager.get().discard(player.getUsername());
            }
        });
    }
}

