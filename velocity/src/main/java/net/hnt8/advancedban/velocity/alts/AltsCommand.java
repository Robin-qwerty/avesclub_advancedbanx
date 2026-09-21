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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            send(sender, "<gray>No shared IPs.</gray>");
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(groups.size() / (double) PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));

        send(sender, "<gold>Alts</gold> <gray>" + page + "/" + totalPages + " · " + groups.size() + "</gray>");

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, groups.size());
        for (int i = from; i < to; i++) {
            AltGroup group = groups.get(i);
            int displayIndex = i + 1;
            String ipDisplay = IpUtils.display(group.getIp(), canSeeIp);
            List<String> banned = bannedAccountNames(group);
            String bannedBit = banned.isEmpty() ? "" : " <red>· " + banned.size() + " banned</red>";
            send(sender, "<hover:show_text:'" + memberListHover(group) + "'>"
                    + "<click:run_command:'/alts view " + displayIndex + "'>"
                    + AltDisplay.scoreSpan(group.getScore(), group.getScore() + "%")
                    + " <white>" + ipDisplay + "</white> <gray>" + group.getMembers().size() + " acc</gray>"
                    + bannedBit
                    + "</click></hover>");
        }

        StringBuilder footer = new StringBuilder();
        if (page > 1) {
            footer.append("<click:run_command:'/alts ").append(page - 1).append("'><gray>[◀]</gray></click> ");
        }
        if (page < totalPages) {
            footer.append("<click:run_command:'/alts ").append(page + 1).append("'><gray>[▶]</gray></click>");
        }
        if (footer.length() > 0) {
            send(sender, footer.toString());
        }
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
            send(sender, "<gray>No tracked players found for <yellow>" + name + "</yellow>.</gray>");
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
        renderGroup(sender, group, canSeeIp, false);
    }

    private void renderGroup(CommandSource sender, AltGroup group, boolean canSeeIp, boolean showBackButton) {
        MethodInterface mi = Universal.get().getMethods();
        SimpleDateFormat fullDate = new SimpleDateFormat(mi.getString(mi.getConfig(), "DateFormat", "dd.MM.yyyy-HH:mm"));
        SimpleDateFormat shortDate = new SimpleDateFormat("dd.MM.yy");
        Set<String> onlineUuids = new HashSet<>();
        for (Object player : mi.getOnlinePlayers()) {
            onlineUuids.add(mi.getInternUUID(player));
        }

        List<TrackedPlayer> members = sortedMembers(group.getMembers(), onlineUuids);
        List<String> banned = bannedAccountNames(group);
        Punishment ipBan = PunishmentManager.get().getBan(group.getIp());
        String ipDisplay = IpUtils.display(group.getIp(), canSeeIp);

        send(sender, "<hover:show_text:'" + reasonsHover(group) + "'>"
                + AltDisplay.scoreSpan(group.getScore(), group.getScore() + "%")
                + "</hover> <white>" + ipDisplay + "</white>"
                + AltDisplay.ipBanTag(ipBan != null)
                + " <gray>" + members.size() + " acc</gray>");

        if (!banned.isEmpty()) {
            send(sender, "<red>Banned:</red> " + clickableNameList(banned));
        }

        for (TrackedPlayer member : members) {
            send(sender, memberLine(member, group.getIp(), canSeeIp, onlineUuids, fullDate, shortDate));
        }

        if (showBackButton) {
            send(sender, "<click:run_command:'/alts'><gray>[◀]</gray></click>");
        }
    }

    private static List<TrackedPlayer> sortedMembers(List<TrackedPlayer> members, Set<String> onlineUuids) {
        List<TrackedPlayer> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> {
            boolean aBan = isAccountBanned(a);
            boolean bBan = isAccountBanned(b);
            if (aBan != bBan) {
                return aBan ? -1 : 1;
            }
            boolean aOn = isOnline(a, onlineUuids);
            boolean bOn = isOnline(b, onlineUuids);
            if (aOn != bOn) {
                return aOn ? -1 : 1;
            }
            return Long.compare(b.getLastJoin(), a.getLastJoin());
        });
        return sorted;
    }

    private static String memberLine(TrackedPlayer member, String groupIp, boolean canSeeIp,
                                     Set<String> onlineUuids, SimpleDateFormat fullDate, SimpleDateFormat shortDate) {
        String name = memberName(member);
        boolean banned = isAccountBanned(member);
        boolean muted = member.getUuid() != null && PunishmentManager.get().isMuted(member.getUuid());
        String nameColor = banned ? "red" : (isOnline(member, onlineUuids) ? "yellow" : "gray");
        String dot = isOnline(member, onlineUuids) ? "<green>●</green>" : "<dark_gray>○</dark_gray>";

        StringBuilder line = new StringBuilder();
        line.append(dot).append(' ');
        line.append("<hover:show_text:'").append(memberHover(member, canSeeIp, fullDate)).append("'>");
        line.append("<click:run_command:'/check ").append(AltDisplay.hoverSafe(name)).append("'>");
        line.append('<').append(nameColor).append('>').append(AltDisplay.hoverSafe(name)).append("</").append(nameColor).append('>');
        line.append("</click></hover>");
        line.append(AltDisplay.banTag(banned));
        line.append(AltDisplay.muteTag(muted));
        line.append(shortDateRange(member, shortDate));
        line.append(otherIpBits(member, groupIp, canSeeIp));
        return line.toString();
    }

    private static String shortDateRange(TrackedPlayer member, SimpleDateFormat shortDate) {
        String first = formatTimestamp(shortDate, member.getFirstSeen());
        long lastTs = member.getLastJoin() > 0 ? member.getLastJoin() : member.getLastLeave();
        String last = formatTimestamp(shortDate, lastTs);
        if ("never".equals(first) && "never".equals(last)) {
            return "";
        }
        if (first.equals(last) || "never".equals(first)) {
            return " <gray>" + last + "</gray>";
        }
        if ("never".equals(last)) {
            return " <gray>" + first + "</gray>";
        }
        return " <gray>" + first + "→" + last + "</gray>";
    }

    private static String otherIpBits(TrackedPlayer member, String groupIp, boolean canSeeIp) {
        String firstIp = member.getFirstIp();
        String lastIp = member.getLastIp();
        StringBuilder extra = new StringBuilder();
        if (lastIp != null && !lastIp.equalsIgnoreCase(groupIp)) {
            Punishment lastIpBan = PunishmentManager.get().getBan(lastIp);
            extra.append(" <gray>now</gray> ").append(IpUtils.display(lastIp, canSeeIp)).append(AltDisplay.ipBanTag(lastIpBan != null));
        }
        if (firstIp != null && !firstIp.equalsIgnoreCase(groupIp) && (lastIp == null || !firstIp.equalsIgnoreCase(lastIp))) {
            Punishment firstIpBan = PunishmentManager.get().getBan(firstIp);
            extra.append(" <gray>was</gray> ").append(IpUtils.display(firstIp, canSeeIp)).append(AltDisplay.ipBanTag(firstIpBan != null));
        }
        return extra.toString();
    }

    private static String memberHover(TrackedPlayer member, boolean canSeeIp, SimpleDateFormat fullDate) {
        String name = memberName(member);
        return "<gray>/check " + AltDisplay.hoverSafe(name)
                + "<br>first " + formatTimestamp(fullDate, member.getFirstSeen())
                + "<br>last join " + formatTimestamp(fullDate, member.getLastJoin())
                + "<br>last seen " + formatTimestamp(fullDate, member.getLastLeave())
                + "<br>first ip " + IpUtils.display(member.getFirstIp(), canSeeIp)
                + "<br>last ip " + IpUtils.display(member.getLastIp(), canSeeIp)
                + "</gray>";
    }

    private static String reasonsHover(AltGroup group) {
        StringBuilder hover = new StringBuilder("<gray>");
        hover.append(AltDisplay.hoverSafe(group.getBand()));
        if (group.getReasons() != null) {
            for (String reason : group.getReasons()) {
                hover.append("<br>• ").append(AltDisplay.hoverSafe(reason));
            }
        }
        hover.append("</gray>");
        return hover.toString();
    }

    private static String memberListHover(AltGroup group) {
        StringBuilder hover = new StringBuilder("<gray>");
        for (TrackedPlayer member : group.getMembers()) {
            hover.append(AltDisplay.hoverSafe(memberName(member)));
            if (isAccountBanned(member)) {
                hover.append(" <red>[BAN]</red>");
            }
            hover.append("<br>");
        }
        hover.append("click for details</gray>");
        return hover.toString();
    }

    private static String clickableNameList(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append("<gray>, </gray>");
            }
            String name = names.get(i);
            out.append("<hover:show_text:'<gray>/check ").append(AltDisplay.hoverSafe(name)).append("</gray>'>");
            out.append("<click:run_command:'/check ").append(AltDisplay.hoverSafe(name)).append("'>");
            out.append("<red>").append(AltDisplay.hoverSafe(name)).append("</red></click></hover>");
        }
        return out.toString();
    }

    private static List<String> bannedAccountNames(AltGroup group) {
        List<String> names = new ArrayList<>();
        for (TrackedPlayer member : group.getMembers()) {
            if (isAccountBanned(member)) {
                names.add(memberName(member));
            }
        }
        return names;
    }

    private static boolean isAccountBanned(TrackedPlayer member) {
        return member.getUuid() != null && PunishmentManager.get().isBanned(member.getUuid());
    }

    private static boolean isOnline(TrackedPlayer member, Set<String> onlineUuids) {
        return member.getUuid() != null && onlineUuids.contains(member.getUuid());
    }

    private static String memberName(TrackedPlayer member) {
        return member.getName() != null ? member.getName() : "unknown";
    }

    private void sendUsage(CommandSource sender) {
        send(sender, "<gray>Usage: /alts [page|player|ip] | /alts view [number]</gray>");
    }

    private void send(CommandSource sender, String miniMessage) {
        Universal.get().getMethods().sendMessage(sender, miniMessage);
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
        if (args.length > 1) {
            return Collections.emptyList();
        }

        String partial = args.length == 1 ? args[0].toLowerCase() : "";
        MethodInterface mi = Universal.get().getMethods();

        // Case-insensitive dedup; online players (always live) take priority over the
        // periodically refreshed recent-players cache for the same name.
        Map<String, String> byLowerName = new LinkedHashMap<>();
        if ("view".startsWith(partial)) {
            byLowerName.put("view", "view");
        }
        for (Object player : mi.getOnlinePlayers()) {
            String name = mi.getName(player);
            if (name != null) {
                byLowerName.put(name.toLowerCase(), name);
            }
        }
        for (String name : RecentPlayerCache.get()) {
            if (name != null) {
                byLowerName.putIfAbsent(name.toLowerCase(), name);
            }
        }

        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, String> entry : byLowerName.entrySet()) {
            if (partial.isEmpty() || entry.getKey().startsWith(partial)) {
                suggestions.add(entry.getValue());
            }
        }
        return suggestions;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Universal.get().hasPerms(invocation.source(), PERM_BASE);
    }

    private static String formatTimestamp(SimpleDateFormat format, long timestamp) {
        return timestamp <= 0 ? "never" : format.format(new Date(timestamp));
    }
}
