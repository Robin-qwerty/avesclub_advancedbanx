package net.hnt8.advancedban.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.manager.PunishmentManager;
import net.hnt8.advancedban.manager.UUIDManager;
import net.hnt8.advancedban.utils.Permissionable;
import net.hnt8.advancedban.utils.Punishment;
import net.hnt8.advancedban.utils.tabcompletion.TabCompleter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class VelocityMethods implements MethodInterface {

    private final File configFile;
    private final File messageFile;
    private final File layoutFile;
    private final File mysqlFile;

    private Map<String, Object> config;
    private Map<String, Object> messages;
    private Map<String, Object> layouts;
    private Map<String, Object> mysql;

    public VelocityMethods() {
        File dataFolder = getDataFolder();
        this.configFile = new File(dataFolder, "config.yml");
        this.messageFile = new File(dataFolder, "Messages.yml");
        this.layoutFile = new File(dataFolder, "Layouts.yml");
        this.mysqlFile = new File(dataFolder, "MySQL.yml");
    }

    @Override
    public void loadFiles() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            copyResourceIfMissing("config.yml", configFile);
            copyResourceIfMissing("Messages.yml", messageFile);
            copyResourceIfMissing("Layouts.yml", layoutFile);

            config = loadYamlMap(configFile);
            messages = loadYamlMap(messageFile);
            layouts = loadYamlMap(layoutFile);
            mysql = mysqlFile.exists() ? loadYamlMap(mysqlFile) : config;
        } catch (IOException ex) {
            getLogger().severe("Failed to load velocity configuration files: " + ex.getMessage());
            Universal.get().debugException(ex);
        }
    }

    private void copyResourceIfMissing(String resourceName, File outFile) throws IOException {
        if (outFile.exists()) {
            return;
        }
        InputStream stream = getPlugin().getClass().getClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IOException("Missing resource " + resourceName + " in velocity module.");
        }
        try (InputStream in = stream) {
            Files.copy(in, outFile.toPath());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYamlMap(File file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Object parsed = yaml.load(reader);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            return new LinkedHashMap<String, Object>();
        }
    }

    @Override
    public String getFromUrlJson(String url, String key) {
        try {
            HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
            request.connect();

            JsonParser parser = new JsonParser();
            JsonObject json = (JsonObject) parser.parse(new InputStreamReader(request.getInputStream()));

            String[] keys = key.split("\\|");
            for (int i = 0; i < keys.length - 1; i++) {
                json = json.getAsJsonObject(keys[i]);
            }
            return json.get(keys[keys.length - 1]).toString().replace("\"", "");
        } catch (Exception exc) {
            return null;
        }
    }

    @Override
    public String getVersion() {
        String version = VelocityMain.get().getClass().getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }

    @Override
    public String[] getKeys(Object file, String path) {
        Object value = getPathValue(file, path);
        if (!(value instanceof Map)) {
            return new String[0];
        }
        Map<?, ?> map = (Map<?, ?>) value;
        List<String> keys = new ArrayList<String>();
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys.toArray(new String[0]);
    }

    @Override
    public Object getConfig() {
        return config;
    }

    @Override
    public Object getMessages() {
        return messages;
    }

    @Override
    public Object getLayouts() {
        return layouts;
    }

    @Override
    public void setupMetrics() {
    }

    @Override
    public boolean isBungee() {
        return true;
    }

    @Override
    public String clearFormatting(String text) {
        return text.replaceAll("<[^>]*>", "").replaceAll("&[0-9a-fk-or]", "");
    }

    @Override
    public VelocityMain getPlugin() {
        return VelocityMain.get();
    }

    @Override
    public File getDataFolder() {
        return getPlugin().getDataDirectory().toFile();
    }

    @Override
    public void setCommandExecutor(String cmd, String permission, TabCompleter tabCompleter) {
        getServer().getCommandManager().register(
                getServer().getCommandManager().metaBuilder(cmd).build(),
                new CommandReceiverVelocity(cmd, permission, tabCompleter)
        );
    }

    @Override
    public void sendMessage(Object player, String msg) {
        if (!(player instanceof CommandSource)) {
            return;
        }
        if (player instanceof Player && BedrockCompat.isBedrock((Player) player)) {
            ((CommandSource) player).sendMessage(Component.text(BedrockCompat.toPlainText(msg)));
            return;
        }
        TextReplacementConfig replacementConfig = TextReplacementConfig.builder()
                .matchLiteral("&")
                .replacement("§")
                .build();
        Component component = MiniMessage.miniMessage().deserialize(msg).replaceText(replacementConfig);
        ((CommandSource) player).sendMessage(component);
    }

    @Override
    public boolean hasPerms(Object player, String perms) {
        return player instanceof CommandSource && ((CommandSource) player).hasPermission(perms);
    }

    @Override
    public Permissionable getOfflinePermissionPlayer(String name) {
        return permission -> false;
    }

    @Override
    public boolean isOnline(String name) {
        return getPlayer(name) != null;
    }

    @Override
    public Player getPlayer(String name) {
        return getServer().getPlayer(name).orElse(null);
    }

    @Override
    public void kickPlayer(String player, String reason) {
        Player target = getPlayer(player);
        if (target == null) {
            return;
        }
        target.disconnect(BedrockCompat.disconnectReason(target, reason));
    }

    @Override
    public Object[] getOnlinePlayers() {
        return getServer().getAllPlayers().toArray();
    }

    @Override
    public void scheduleAsyncRep(Runnable rn, long l1, long l2) {
        getServer().getScheduler().buildTask(getPlugin(), rn)
                .delay(l1 * 50, TimeUnit.MILLISECONDS)
                .repeat(l2 * 50, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Override
    public void scheduleAsync(Runnable rn, long l1) {
        getServer().getScheduler().buildTask(getPlugin(), rn)
                .delay(l1 * 50, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Override
    public void runAsync(Runnable rn) {
        getServer().getScheduler().buildTask(getPlugin(), rn).schedule();
    }

    @Override
    public void runSync(Runnable rn) {
        rn.run();
    }

    @Override
    public void executeCommand(String cmd) {
        getServer().getCommandManager().executeAsync(getServer().getConsoleCommandSource(), cmd);
    }

    @Override
    public String getName(Object player) {
        if (player instanceof Player) {
            return ((Player) player).getUsername();
        }
        return "CONSOLE";
    }

    @Override
    public String getName(String uuid) {
        Player player = getServer().getPlayer(UUID.fromString(uuid)).orElse(null);
        return player == null ? null : player.getUsername();
    }

    @Override
    public String getIP(Object player) {
        if (!(player instanceof Player)) {
            return null;
        }
        return ((Player) player).getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public String getInternUUID(Object player) {
        if (!(player instanceof Player)) {
            return "none";
        }
        return ((Player) player).getUniqueId().toString().replace("-", "");
    }

    @Override
    public String getInternUUID(String player) {
        Player target = getPlayer(player);
        return target == null ? null : target.getUniqueId().toString().replace("-", "");
    }

    @Override
    public boolean callChat(Object player) {
        if (!(player instanceof Player)) {
            return false;
        }
        Player velocityPlayer = (Player) player;
        String serverName = velocityPlayer.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse(null);
        Punishment mute = PunishmentManager.get().getMute(
                UUIDManager.get().getUUID(getName(player)),
                serverName
        );
        if (mute == null) {
            return false;
        }
        for (String str : mute.getLayout()) {
            sendMessage(player, str);
        }
        return true;
    }

    @Override
    public boolean callCMD(Object player, String cmd) {
        if (!(player instanceof Player) || cmd.length() < 2) {
            return false;
        }
        if (!Universal.get().isMuteCommand(cmd.substring(1))) {
            return false;
        }
        Player velocityPlayer = (Player) player;
        String serverName = velocityPlayer.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse(null);
        Punishment mute = PunishmentManager.get().getMute(
                UUIDManager.get().getUUID(getName(player)),
                serverName
        );
        if (mute == null) {
            return false;
        }
        for (String str : mute.getLayout()) {
            sendMessage(player, str);
        }
        return true;
    }

    @Override
    public Object getMySQLFile() {
        return mysql;
    }

    @Override
    public String parseJSON(InputStreamReader json, String key) {
        JsonElement element = new JsonParser().parse(json);
        if (element instanceof JsonNull) {
            return null;
        }
        JsonElement obj = ((JsonObject) element).get(key);
        return obj != null ? obj.toString().replace("\"", "") : null;
    }

    @Override
    public String parseJSON(String json, String key) {
        JsonElement element = new JsonParser().parse(json);
        if (element instanceof JsonNull) {
            return null;
        }
        JsonElement obj = ((JsonObject) element).get(key);
        return obj != null ? obj.toString().replace("\"", "") : null;
    }

    @Override
    public Boolean getBoolean(Object file, String path) {
        Object value = getPathValue(file, path);
        return value instanceof Boolean ? (Boolean) value : Boolean.FALSE;
    }

    @Override
    public String getString(Object file, String path) {
        Object value = getPathValue(file, path);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public Long getLong(Object file, String path) {
        Object value = getPathValue(file, path);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    @Override
    public Integer getInteger(Object file, String path) {
        Object value = getPathValue(file, path);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    @Override
    public List<String> getStringList(Object file, String path) {
        Object value = getPathValue(file, path);
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) value;
        List<String> list = new ArrayList<String>(raw.size());
        for (Object item : raw) {
            list.add(String.valueOf(item));
        }
        return list;
    }

    @Override
    public boolean getBoolean(Object file, String path, boolean def) {
        Object value = getPathValue(file, path);
        return value instanceof Boolean ? (Boolean) value : def;
    }

    @Override
    public String getString(Object file, String path, String def) {
        String value = getString(file, path);
        return value == null ? def : value;
    }

    @Override
    public long getLong(Object file, String path, long def) {
        Long value = getLong(file, path);
        return value == null ? def : value;
    }

    @Override
    public int getInteger(Object file, String path, int def) {
        Integer value = getInteger(file, path);
        return value == null ? def : value;
    }

    @Override
    public boolean contains(Object file, String path) {
        return getPathValue(file, path) != null;
    }

    @Override
    public String getFileName(Object file) {
        return "[Only available on Bukkit-Version!]";
    }

    @Override
    public void callPunishmentEvent(Punishment punishment) {
    }

    @Override
    public void callRevokePunishmentEvent(Punishment punishment, boolean massClear) {
    }

    @Override
    public boolean isOnlineMode() {
        return getServer().getConfiguration().isOnlineMode();
    }

    @Override
    public void notify(String perm, List<String> notification) {
        for (Player player : getServer().getAllPlayers()) {
            if (hasPerms(player, perm)) {
                for (String line : notification) {
                    sendMessage(player, line);
                }
            }
        }
    }

    @Override
    public Logger getLogger() {
        return VelocityMain.get().getLogger();
    }

    @Override
    public boolean isUnitTesting() {
        return false;
    }

    @Override
    public String getServerName(Object player) {
        // For Velocity, get the server name where the player is currently connected
        if (player instanceof Player) {
            Player velocityPlayer = (Player) player;
            // Get the server the player is currently connected to
            return velocityPlayer.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse(null);
        }
        // For console commands, check ThreadLocal for server name (set by backend link)
        String serverName = net.hnt8.advancedban.Universal.getCurrentServerName();
        if (serverName != null) {
            return serverName;
        }
        // Fallback: return proxy server name or null
        return getServer().getConfiguration().getServers().keySet().iterator().hasNext() 
            ? "proxy" : null;
    }

    private ProxyServer getServer() {
        return VelocityMain.get().getServer();
    }

    @SuppressWarnings("unchecked")
    private Object getPathValue(Object file, String path) {
        if (!(file instanceof Map)) {
            return null;
        }
        Map<String, Object> current = (Map<String, Object>) file;
        String[] parts = path.split("\\.");
        Object value = current;

        for (String part : parts) {
            if (!(value instanceof Map)) {
                return null;
            }
            Map<String, Object> map = (Map<String, Object>) value;
            value = map.get(part);
        }
        return value;
    }
}

