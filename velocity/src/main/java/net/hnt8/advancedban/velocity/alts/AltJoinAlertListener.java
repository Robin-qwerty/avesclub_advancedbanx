package net.hnt8.advancedban.velocity.alts;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.PlayerManager;
import net.hnt8.advancedban.manager.UUIDManager;
import net.hnt8.advancedban.utils.TrackedPlayer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alerts staff when a player who just joined shares an IP with a banned account - the
 * player themselves isn't banned (they'd have been denied already), but their history
 * shares an address with someone who is, which is worth a look.
 * <p>
 * Gated behind {@code avesban.alt.notify} so it's an opt-in, per-staff-member alert (grant
 * or revoke it for whichever admins want it) - not something tied to the joining player or
 * their IP, which can't sensibly be "muted" per-target without hiding the same evasion this
 * is meant to catch.
 */
public class AltJoinAlertListener {

    private static final String PERM_NOTIFY = "avesban.alt.notify";
    private static final String PERM_IP = "avesban.alt.ip";

    private final ProxyServer server;

    public AltJoinAlertListener(ProxyServer server) {
        this.server = server;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        Universal.get().getMethods().runAsync(() -> check(player));
    }

    private void check(Player player) {
        String uuid = UUIDManager.get().getUUID(player.getUsername().toLowerCase());
        if (uuid == null) {
            return;
        }

        TrackedPlayer record = PlayerManager.get().getByUuid(uuid);
        if (record == null) {
            return;
        }

        List<TrackedPlayer> banned = AltAccountService.findBannedLinkedAccounts(uuid, record);
        if (banned.isEmpty()) {
            return;
        }

        String ip = record.getLastIp() != null ? record.getLastIp() : record.getFirstIp();
        AltGroup group = ip != null ? AltAccountService.findGroupForIp(ip) : null;

        String bannedNames = banned.stream()
                .map(p -> p.getName() != null ? p.getName() : "unknown")
                .collect(Collectors.joining(", "));

        MethodInterface mi = Universal.get().getMethods();
        for (Player online : server.getAllPlayers()) {
            if (!Universal.get().hasPerms(online, PERM_NOTIFY)) {
                continue;
            }

            boolean canSeeIp = Universal.get().hasPerms(online, PERM_IP);
            String ipDisplay = ip != null ? IpUtils.display(ip, canSeeIp) : "unknown";

            StringBuilder msg = new StringBuilder();
            msg.append("<gold><bold>Alt Alert</bold></gold> <gray>-</gray> ")
                    .append("<hover:show_text:'<gray>Click to view details</gray>'>")
                    .append("<click:run_command:'/alts ").append(player.getUsername()).append("'>")
                    .append("<yellow>").append(player.getUsername()).append("</yellow>")
                    .append("</click></hover>")
                    .append(" <gray>just joined on</gray> <white>").append(ipDisplay).append("</white>")
                    .append(" <gray>- shared with banned account(s):</gray> <red>").append(bannedNames).append("</red>");
            if (group != null) {
                msg.append(" ").append(AltDisplay.scoreSpan(group.getScore(), group.getScore() + "%"));
            }

            mi.sendMessage(online, msg.toString());
        }
    }
}
