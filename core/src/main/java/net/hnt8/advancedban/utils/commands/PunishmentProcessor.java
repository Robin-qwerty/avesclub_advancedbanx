package net.hnt8.advancedban.utils.commands;

import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.MessageManager;
import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.manager.TimeManager;
import net.hnt8.advancedban.utils.Command;
import net.hnt8.advancedban.utils.Permissionable;
import net.hnt8.advancedban.utils.Punishment;
import net.hnt8.advancedban.utils.PunishmentType;
import net.hnt8.advancedban.utils.CommandUtils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PunishmentProcessor implements Consumer<Command.CommandInput> {
    private PunishmentType type;

    public PunishmentProcessor(PunishmentType type) {
        this.type = type;
    }

    @Override
    public void accept(Command.CommandInput input) {
        boolean silent = processTag(input, "-s");
        String name = input.getPrimary();

        // extract target
        String target = type.isIpOrientated()
                ? CommandUtils.processIP(input)
                : CommandUtils.processName(input);
        if (target == null)
            return;

        // is exempted
        if (processExempt(name, target, input.getSender(), type))
            return;

        // calculate duration if necessary
        Long end = -1L;
        String timeTemplate = "";
        if (type.isTemp()) {
            TimeCalculation calculation = processTime(input, target, type);
            if (calculation == null)
                return;

            end = calculation.time;

            if (calculation.template != null)
                timeTemplate = calculation.template;
        }


        // Optional scoped server parameter (Velocity/Bungee only)
        // The last argument is treated as server only if it is explicitly listed in config.yml.
        String targetServer = extractTargetServer(input);

        // build reason (after potentially removing server name)
        String reason = CommandUtils.processReason(input);
        if (reason == null)
            return;
        else if (reason.isEmpty())
            reason = null;

        if (reason == null && !timeTemplate.isEmpty()) {
            reason = "@" + timeTemplate;
        }

        // check if punishment of this type is already active (checking both network-wide and server-specific)
        if (alreadyPunished(target, type, targetServer)) {
            MessageManager.sendMessage(input.getSender(), type.getBasic().getName() + ".AlreadyDone",
                    true, "NAME", name);
            return;
        }

        MethodInterface mi = Universal.get().getMethods();
        String operator = mi.getName(input.getSender());
        String server = mi.getServerName(input.getSender());
        Punishment.create(name, target, reason, operator, type, end, timeTemplate, server, targetServer, silent);

        MessageManager.sendMessage(input.getSender(), type.getBasic().getName() + ".Done",
                true, "NAME", name);
    }

    // Removes time argument and returns timestamp (null if failed)
    private static TimeCalculation processTime(Command.CommandInput input, String uuid, PunishmentType type) {
        String time = input.getPrimary();
        input.next();
        MethodInterface mi = Universal.get().getMethods();
        if (time.matches("#.+")) {
            String layout = time.substring(1);
            if (!mi.contains(mi.getLayouts(), "Time." + layout)) {
                MessageManager.sendMessage(input.getSender(), "General.LayoutNotFound", true, "NAME", layout);
                return null;
            }
            int i = PunishmentManager.get().getCalculationLevel(uuid, layout);
            List<String> timeLayout = mi.getStringList(mi.getLayouts(), "Time." + layout);
            String timeName = timeLayout.get(Math.min(i, timeLayout.size() - 1));
            if (timeName.equalsIgnoreCase("perma")) {
                return new TimeCalculation(layout, -1L);
            }
            Long actualTime = TimeManager.getTime() + TimeManager.toMilliSec(timeName);
            return new TimeCalculation(layout, actualTime);
        }
        long toAdd = TimeManager.toMilliSec(time);
        if (!Universal.get().hasPerms(input.getSender(), "avesban." + type.getName() + ".dur.max")) {
            long max = -1;
            for (int i = 10; i >= 1; i--) {
                if (Universal.get().hasPerms(input.getSender(), "avesban." + type.getName() + ".dur." + i) &&
                        mi.contains(mi.getConfig(), "TempPerms." + i)) {
                    max = mi.getLong(mi.getConfig(), "TempPerms." + i) * 1000;
                    break;
                }
            }
            if (max != -1 && toAdd > max) {
                MessageManager.sendMessage(input.getSender(), type.getName() + ".MaxDuration", true, "MAX", max / 1000 + "");
                return null;
            }
        }
        return new TimeCalculation(null, TimeManager.getTime() + toAdd);
    }

    // Checks whether target is exempted from punishment
    private static boolean processExempt(String name, String target, Object sender, PunishmentType type) {
        MethodInterface mi = Universal.get().getMethods();
        String dataName = name.toLowerCase();

        boolean exempt = false;
        if (mi.isOnline(dataName)) {
            Object onlineTarget = mi.getPlayer(dataName);
            exempt = canNotPunish(legacyFallback((perms) -> mi.hasPerms(sender, perms)),
                    legacyFallback((perms) -> mi.hasPerms(onlineTarget, perms)), type.getName());
        } else {
            final Permissionable offlinePermissionPlayer = mi.getOfflinePermissionPlayer(name);
            exempt = Universal.get().isExemptPlayer(dataName) ||
                    canNotPunish(legacyFallback((perms) -> mi.hasPerms(sender, perms)),
                            legacyFallback(offlinePermissionPlayer::hasPermission), type.getName());
        }

        if (exempt) {
            MessageManager.sendMessage(sender, type.getBasic().getName() + ".Exempt",
                    true, "NAME", name);
            return true;
        }
        return false;
    }

    // Check based on exempt level if some is able to ban a player
    public static boolean canNotPunish(Function<String, Boolean> operatorHasPerms, Function<String, Boolean> targetHasPerms, String path) {
        final String perms = "avesban." + path + ".exempt";
        if (targetHasPerms.apply(perms))
            return true;

        int targetLevel = permissionLevel(targetHasPerms, perms);

        return targetLevel != 0 && permissionLevel(operatorHasPerms, perms) <= targetLevel;
    }

    // Permissions were renamed from "ab." to "avesban."; fall back to the old node
    // so exempt-level grants made before the rename keep working.
    private static Function<String, Boolean> legacyFallback(Function<String, Boolean> checker) {
        return perms -> {
            if (checker.apply(perms)) {
                return true;
            }
            if (perms.startsWith("avesban.")) {
                return checker.apply("ab." + perms.substring("avesban.".length()));
            }
            return false;
        };
    }

    private static int permissionLevel(Function<String, Boolean> hasPerms, String permission) {
        for (int i = 10; i >= 1; i--)
            if (hasPerms.apply(permission + "." + i))
                return i;
        return 0;
    }

    // Checks whether input contains tag and removes it
    private static boolean processTag(Command.CommandInput input, String tag) {
        // Check the first few arguments for the tag
        String[] args = input.getArgs();
        for (int i = 0; i < args.length && i < 4; i++) {
            if (tag.equalsIgnoreCase(args[i])) {
                input.removeArgument(i);
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyPunished(String target, PunishmentType type, String targetServer) {
        if (type.getBasic() == PunishmentType.MUTE) {
            for (Punishment mute : PunishmentManager.get().getPunishments(target, PunishmentType.MUTE, true)) {
                if (targetServer == null) {
                    // Network-wide mute already exists
                    if (mute.getTargetServer() == null) {
                        return true;
                    }
                } else {
                    // Target server already muted or network-wide mute exists
                    if (mute.getTargetServer() == null || targetServer.equalsIgnoreCase(mute.getTargetServer())) {
                        return true;
                    }
                }
            }
        } else if (type.getBasic() == PunishmentType.BAN) {
            for (Punishment ban : PunishmentManager.get().getPunishments(target, PunishmentType.BAN, true)) {
                if (targetServer == null) {
                    // Network-wide ban already exists
                    if (ban.getTargetServer() == null) {
                        return true;
                    }
                } else {
                    // Target server already banned or network-wide ban exists
                    if (ban.getTargetServer() == null || targetServer.equalsIgnoreCase(ban.getTargetServer())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String extractTargetServer(Command.CommandInput input) {
        if (!Universal.get().isBungee() || !input.hasNext()) {
            return null;
        }

        MethodInterface mi = Universal.get().getMethods();
        List<String> knownServers = mi.getStringList(mi.getConfig(), "ScopedPunishmentServers");
        if (knownServers == null || knownServers.isEmpty()) {
            return null;
        }

        String[] remainingArgs = input.getArgs();
        if (remainingArgs.length == 0) {
            return null;
        }

        String lastArg = remainingArgs[remainingArgs.length - 1];
        for (String server : knownServers) {
            if (server != null && server.equalsIgnoreCase(lastArg)) {
                input.removeArgument(remainingArgs.length - 1);
                return server;
            }
        }
        return null;
    }

    private static class TimeCalculation {
        private String template;
        private Long time;

        public TimeCalculation(String template, Long time) {
            this.template = template;
            this.time = time;
        }
    }
}
