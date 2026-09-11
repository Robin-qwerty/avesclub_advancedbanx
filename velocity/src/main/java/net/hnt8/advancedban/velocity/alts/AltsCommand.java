package net.hnt8.advancedban.velocity.alts;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.MessageManager;
import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.utils.Punishment;
import net.hnt8.advancedban.utils.TrackedPlayer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * {@code /alts} and {@code /altaccounts} - lists IP addresses shared by more than
 * one tracked player and lets staff drill into each one to see who's behind it.
 * <p>
 * Entirely proxy-side: reads the shared Players table via core's PlayerManager,
 * so it needs no changes on the backend (Paper) servers to work.
 */
public class AltsCommand implements SimpleCommand {

    private static final String PERM_BASE = "avesban.alt";
    private static final String PERM_IP = "avesban.alt.ip";
    private static final int PAGE_SIZE = 8;

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();

        if (!Universal.get().hasPerms(sender, PERM_BASE)) {
            MessageManager.sendMessage(sender, "General.NoPerms", true);
            return;
        }

        boolean canSeeIp = Universal.get().hasPerms(sender, PERM_IP);
        String[] args = invocation.arguments();

        if (args.length >= 2 && args[0].equalsIgnoreCase("view")) {
            Integer index = parseInt(args[1]);
            if (index == null) {
                sendUsage(sender);
                return;
            }
            showDetail(sender, index, canSeeIp);
            return;
        }

        if (args.length >= 1) {
            Integer page = parseInt(args[0]);
            if (page != null) {
                showList(sender, page, canSeeIp);
            } else if (IpUtils.looksLikeIp(args[0])) {
                showIpLookup(sender, args[0], canSeeIp);
            } else {
                showPlayerLookup(sender, args[0], canSeeIp);
            }
            return;
        }

        showList(sender, 1, canSeeIp);
    }

    private void showList(CommandSource sender, int page, boolean canSeeIp) {
        List<AltGroup> groups = AltAccountService.listGroups();
        if (groups.isEmpty()) {
            send(sender, "<gray>No IP addresses are currently shared by more than one account - no potential alts detected.</gray>");
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(groups.size() / (double) PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));

        send(sender, "<gold><bold>Alt Accounts</bold></gold> <gray>- page " + page + "/" + totalPages
                + " (" + groups.size() + " shared address" + (groups.size() == 1 ? "" : "es") + ")</gray>");

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, groups.size());
        for (int i = from; i < to; i++) {
            AltGroup group = groups.get(i);
            int displayIndex = i + 1;
            String ipDisplay = IpUtils.display(group.getIp(), canSeeIp);
            send(sender, "<hover:show_text:'<gray>Click to view details for this address</gray>'>"
                    + "<click:run_command:'/alts view " + displayIndex + "'>"
                    + scoreSpan(group.getScore(), group.getScore() + "%")
                    + " <white>" + ipDisplay + "</white> <gray>- " + group.getMembers().size() + " accounts</gray>"
                    + "</click></hover>");
        }

        StringBuilder footer = new StringBuilder();
        if (page > 1) {
            footer.append("<click:run_command:'/alts ").append(page - 1).append("'><gray>[◀ Prev]</gray></click> ");
        }
        if (page < totalPages) {
            footer.append("<click:run_command:'/alts ").append(page + 1).append("'><gray>[Next ▶]</gray></click>");
        }
        if (footer.length() > 0) {
            send(sender, footer.toString());
        }
        send(sender, "<gray><italic>Click an address to view details, or run /alts [player|ip] to check something directly.</italic></gray>");
    }

    private void showDetail(CommandSource sender, int index, boolean canSeeIp) {
        List<AltGroup> groups = AltAccountService.listGroups();
        if (index < 1 || index > groups.size()) {
            send(sender, "<red>That entry no longer exists - the list may have changed. Run /alts again.</red>");
            return;
        }
        renderGroup(sender, groups.get(index - 1), canSeeIp, true);
    }

    private void showPlayerLookup(CommandSource sender, String name, boolean canSeeIp) {
        AltGroup group = AltAccountService.findGroupForPlayer(name);
        if (group == null) {
            send(sender, "<gray>" + name + " doesn't share an IP address with any other tracked account.</gray>");
            return;
        }
        renderGroup(sender, group, canSeeIp, false);
    }

    private void showIpLookup(CommandSource sender, String ip, boolean canSeeIp) {
        AltGroup group = AltAccountService.findGroupForIp(ip);
        String ipDisplay = IpUtils.display(ip, canSeeIp);
        if (group == null) {
            send(sender, "<gray>No tracked players have used " + ipDisplay + ".</gray>");
            return;
        }
        if (group.getMembers().size() == 1) {
            TrackedPlayer only = group.getMembers().get(0);
            String name = only.getName() != null ? only.getName() : "unknown";
            send(sender, "<gray>Only one tracked account has used " + ipDisplay + ":</gray> "
                    + "<hover:show_text:'<gray>Click to run /check " + name + "</gray>'>"
                    + "<click:run_command:'/check " + name + "'><yellow>" + name + "</yellow></click></hover>");
            return;
        }
        renderGroup(sender, group, canSeeIp, false);
    }

    private void renderGroup(CommandSource sender, AltGroup group, boolean canSeeIp, boolean showBackButton) {
        MethodInterface mi = Universal.get().getMethods();
        SimpleDateFormat format = new SimpleDateFormat(mi.getString(mi.getConfig(), "DateFormat", "dd.MM.yyyy-HH:mm"));
        Set<String> onlineUuids = new HashSet<>();
        for (Object player : mi.getOnlinePlayers()) {
            onlineUuids.add(mi.getInternUUID(player));
        }

        String ipDisplay = IpUtils.display(group.getIp(), canSeeIp);
        send(sender, "<gold><bold>Alt Group</bold></gold> <gray>-</gray> <white>" + ipDisplay + "</white>  "
                + scoreSpan(group.getScore(), group.getScore() + "% - " + group.getBand()));

        send(sender, "<gray>Why:</gray>");
        for (String reason : group.getReasons()) {
            send(sender, "<gray> - " + reason + "</gray>");
        }

        send(sender, "<gray>Accounts on this address (" + group.getMembers().size() + "):</gray>");
        for (TrackedPlayer member : group.getMembers()) {
            boolean online = member.getUuid() != null && onlineUuids.contains(member.getUuid());
            String name = member.getName() != null ? member.getName() : "unknown";
            String status = online ? "<green>● online</green>" : "<gray>● offline</gray>";

            send(sender, "<hover:show_text:'<gray>Click to run /check " + name + "</gray>'>"
                    + "<click:run_command:'/check " + name + "'><yellow>" + name + "</yellow></click></hover> " + status);
            send(sender, "<gray>    first joined:</gray> " + formatTimestamp(format, member.getFirstSeen())
                    + " <gray>| last joined:</gray> " + formatTimestamp(format, member.getLastJoin())
                    + " <gray>| last seen:</gray> " + formatTimestamp(format, member.getLastLeave()));

            String firstIp = member.getFirstIp();
            String lastIp = member.getLastIp();
            boolean differs = firstIp != null && lastIp != null && !firstIp.equalsIgnoreCase(lastIp);
            Punishment lastIpBan = lastIp != null ? PunishmentManager.get().getBan(lastIp) : null;
            Punishment firstIpBan = differs ? PunishmentManager.get().getBan(firstIp) : null;

            if (differs) {
                send(sender, "<gray>    first ip:</gray> " + IpUtils.display(firstIp, canSeeIp) + banTag(firstIpBan)
                        + " <gray>| current ip:</gray> " + IpUtils.display(lastIp, canSeeIp) + banTag(lastIpBan));
            } else if (lastIp != null) {
                send(sender, "<gray>    ip:</gray> " + IpUtils.display(lastIp, canSeeIp) + banTag(lastIpBan));
            }

            boolean linkedBanned = !PunishmentManager.get().getBannedAccountsOnIp(lastIp, member.getUuid()).isEmpty()
                    || (differs && !PunishmentManager.get().getBannedAccountsOnIp(firstIp, member.getUuid()).isEmpty());
            if (linkedBanned) {
                send(sender, "<yellow>    ⚠ another account on one of these IPs is banned</yellow>");
            }
        }

        if (showBackButton) {
            send(sender, "<click:run_command:'/alts'><gray>[◀ Back to list]</gray></click>");
        }
    }

    private void sendUsage(CommandSource sender) {
        send(sender, "<gray>Usage: /alts [page|player|ip] | /alts view [number]</gray>");
    }

    private void send(CommandSource sender, String miniMessage) {
        Universal.get().getMethods().sendMessage(sender, miniMessage);
    }

    private String banTag(Punishment ban) {
        return ban == null ? "" : " <red>[BANNED]</red>";
    }

    private String scoreSpan(int score, String label) {
        String tag = scoreTagName(score);
        return "<" + tag + ">[" + label + "]</" + tag + ">";
    }

    private String scoreTagName(int score) {
        if (score >= 75) return "red";
        if (score >= 50) return "gold";
        if (score >= 25) return "yellow";
        return "green";
    }

    private Integer parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource sender = invocation.source();
        if (!Universal.get().hasPerms(sender, PERM_BASE)) {
            return Collections.emptyList();
        }

        String[] args = invocation.arguments();
        if (args.length <= 1) {
            MethodInterface mi = Universal.get().getMethods();
            List<String> suggestions = new ArrayList<>();
            suggestions.add("view");
            for (Object player : mi.getOnlinePlayers()) {
                suggestions.add(mi.getName(player));
            }
            return suggestions;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Universal.get().hasPerms(invocation.source(), PERM_BASE);
    }

    private static String formatTimestamp(SimpleDateFormat format, long timestamp) {
        return timestamp <= 0 ? "never" : format.format(new Date(timestamp));
    }
}
