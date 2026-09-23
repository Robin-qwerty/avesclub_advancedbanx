package net.hnt8.advancedban.manager;

import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.utils.TrackedPlayer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.UUID;

/**
 * The UUID Manager used to resolve and cache the UUIDs.
 */
public class UUIDManager {
    private static UUIDManager instance = null;
    private FetcherMode mode;
    private final Map<String, String> activeUUIDs = new HashMap<>();
    
    private MethodInterface mi() {
    	return Universal.get().getMethods();
    }

    /**
     * Get the uuid manager.
     *
     * @return the uuid manager instance
     */
    public static synchronized UUIDManager get() {
        return instance == null ? instance = new UUIDManager() : instance;
    }

    /**
     * Initially setup the uuid manager by determening which {@link FetcherMode} should be used
     * based on the configured preference and the servers capabilities.
     */
    public void setup() {
    	MethodInterface mi = mi();
        if (mi.getBoolean(mi.getConfig(), "UUID-Fetcher.Dynamic", true)) {
            if (!mi.isOnlineMode()) {
                mode = FetcherMode.DISABLED;
            } else {
                if (Universal.get().isBungee()) {
                    mode = FetcherMode.MIXED;
                } else {
                    mode = FetcherMode.INTERN;
                }
            }
        } else {
            if (!mi.getBoolean(mi.getConfig(), "UUID-Fetcher.Enabled", true)) {
                mode = FetcherMode.DISABLED;
            } else if (mi.getBoolean(mi.getConfig(), "UUID-Fetcher.Intern", false)) {
                mode = FetcherMode.INTERN;
            } else {
                mode = FetcherMode.RESTFUL;
            }
        }
    }

    /**
     * Initially request the uuid bypassing the cache.<br>
     * If request succeeds the uuid will be automatically entered into the cache.
     *
     * @param name the name
     * @return the uuid
     */
    public String getInitialUUID(String name) {
        MethodInterface mi = mi();
        name = name.toLowerCase();
        boolean floodgate = isFloodgateName(name);

        if (mode == FetcherMode.DISABLED && !floodgate) {
            return name;
        }

        if (mode == FetcherMode.INTERN || mode == FetcherMode.MIXED || floodgate) {
            for (String variant : nameVariants(name)) {
                String internUUID = mi.getInternUUID(variant);
                if (internUUID != null) {
                    remember(name, internUUID);
                    return internUUID;
                }
            }
            if (mode == FetcherMode.INTERN && !floodgate) {
                return null;
            }
        }

        for (String variant : nameVariants(name)) {
            TrackedPlayer record = PlayerManager.get().getByName(variant);
            if (record != null && record.getUuid() != null && !record.getUuid().isEmpty()) {
                remember(name, record.getUuid());
                remember(variant, record.getUuid());
                return record.getUuid();
            }
        }

        // Floodgate/Bedrock names are not Mojang accounts. Hitting api.mojang.com with
        // ".player" only produces FailedFetch noise and never returns a UUID.
        if (floodgate) {
            return null;
        }

        if (mode == FetcherMode.DISABLED) {
            return name;
        }

        String uuid = null;
        try {
            uuid = askAPI(mi.getString(mi.getConfig(), "UUID-Fetcher.REST-API.URL"), name, mi.getString(mi.getConfig(), "UUID-Fetcher.REST-API.Key"));
        } catch (IOException e) {
            System.out.println("Error -> " + e.getMessage());
            System.out.println("!! Failed fetching UUID of " + name);
            System.out.println("!! Could not connect to REST-API under " + mi.getString(mi.getConfig(), "UUID-Fetcher.REST-API.URL"));
        }

        if (uuid == null) {
            System.out.println("Trying to fetch UUID form BackUp-API...");
            try {
                uuid = askAPI(mi.getString(mi.getConfig(), "UUID-Fetcher.BackUp-API.URL"), name, mi.getString(mi.getConfig(), "UUID-Fetcher.BackUp-API.Key"));
            } catch (IOException e) {
                System.out.println("!! Failed fetching UUID of " + name);
                System.out.println("!! Could not connect to REST-API under " + mi.getString(mi.getConfig(), "UUID-Fetcher.BackUp-API.URL"));
            }
        }

        if (uuid == null) {
            System.out.println("!! !! Warning we have not been able to fetch the UUID of the Player " + name);
            System.out.println("!! Make sure that the name is spelled correctly and if it is change your UUID-Fetcher settings!");
        }

        return uuid;
    }

    private void remember(String name, String uuid) {
        if (name != null && uuid != null) {
            activeUUIDs.put(name.toLowerCase(), uuid);
        }
    }

    /**
     * Floodgate Bedrock names use a leading {@code .} by default and are not Mojang accounts.
     */
    public static boolean isFloodgateName(String name) {
        return name != null && name.startsWith(".") && name.length() > 1;
    }

    static List<String> nameVariants(String name) {
        String lower = name.toLowerCase();
        List<String> variants = new ArrayList<String>();
        variants.add(lower);
        if (isFloodgateName(lower)) {
            variants.add(lower.substring(1));
        } else if (!lower.isEmpty() && !lower.startsWith(".")) {
            variants.add("." + lower);
        }
        return variants;
    }

    /**
     * Adds uuid to the cache
     *
     * @param name the name
     * @param uuid the uuid
     */
    public void supplyInternUUID(String name, UUID uuid) {
        if (mode == FetcherMode.INTERN || mode == FetcherMode.MIXED) {
            activeUUIDs.put(name.toLowerCase(), uuid.toString().replace("-", ""));
        }
    }

    /**
     * Convert String to UUID even if dashes are missing
     *
     * @param uuid
     * @return
     */
    public UUID fromString(String uuid) {
        if (!uuid.contains("-") && uuid.length() == 32)
            uuid = uuid
                    .replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");

        return uuid.length() == 36 && uuid.contains("-") ? UUID.fromString(uuid) : null;
    }

    /**
     * Get the uuid to a name.
     *
     * @param name the name
     * @return the uuid
     */
    public String getUUID(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (String variant : nameVariants(name)) {
            String inMemoryUuid = getInMemoryUUID(variant);
            if (inMemoryUuid != null) {
                return inMemoryUuid;
            }
        }
        return getInitialUUID(name);
    }

    /**
     * Gets a uuid from a name only if AdvancedBan
     * already has the uuid/name mapping in memory.
     *
     * @param name the player name
     * @return the nonhyphenated uuid or null if not found
     */
    public String getInMemoryUUID(String name) {
        return activeUUIDs.get(name.toLowerCase());
    }

    /**
     * Gets a name from a uuid only if AdvancedBan
     * already has the uuid/name mapping in memory.
     *
     * @param uuid the uuid without hyphens
     * @return the player name or null if not found
     */
    public String getInMemoryName(String uuid) {
        for (Entry<String, String> rs : activeUUIDs.entrySet()) {
            if (rs.getValue().equalsIgnoreCase(uuid)) {
                return rs.getKey();
            }
        }
        return null;
    }

    /**
     * Get name from an uuid.
     *
     * @param uuid         the uuid
     * @param forceInitial whether to bypass the cache
     * @return the name from uuid
     */
    public String getNameFromUUID(String uuid, boolean forceInitial) {
    	MethodInterface mi = mi();
        if (mode == FetcherMode.DISABLED)
            return uuid;

        if (mode == FetcherMode.INTERN || mode == FetcherMode.MIXED) {
            String internName = mi.getName(uuid);
            if (mode == FetcherMode.INTERN || internName != null)
                return internName;
        }

        if (!forceInitial) {
            String inMemoryName = getInMemoryName(uuid);
            if (inMemoryName != null) {
                return inMemoryName;
            }
        }

        try (Scanner scanner = new Scanner(new URL("https://api.mojang.com/user/profiles/" + uuid + "/names").openStream(), "UTF-8")) {
            String s = scanner.useDelimiter("\\A").next();
            s = s.substring(s.lastIndexOf('{'), s.lastIndexOf('}') + 1);
            return mi.parseJSON(s, "name");
        } catch (Exception exc) {
            return null;
        }
    }



    private String askAPI(String url, String name, String key) throws IOException {
    	MethodInterface mi = mi();
        name = name.toLowerCase();
        HttpURLConnection request = (HttpURLConnection) new URL(url.replaceAll("%NAME%", name).replaceAll("%TIMESTAMP%", new Date().getTime() + "")).openConnection();
        request.connect();

        String uuid = mi.parseJSON(new InputStreamReader(request.getInputStream()), key);

        if (uuid == null) {
            System.out.println("!! Failed fetching UUID of " + name);
            System.out.println("!! Could not find key '" + key + "' in the servers response");
            System.out.println("!! Response: " + request.getResponseMessage());
        } else {
            activeUUIDs.put(name, uuid);
        }
        return uuid;
    }

    /**
     * Get the {@link FetcherMode} which is used.
     *
     * @return the mode
     */
    public FetcherMode getMode() {
        return mode;
    }

    /**
     * The fetcher-mode describes how the {@link UUIDManager} resolves UUIDs.
     */
    public enum FetcherMode {
        /**
         * No UUID Fetcher is used. The Username will be treated as an UUID.<br>
         * <b>Recommended for:</b> Servers running in offline mode (cracked).
         */
        DISABLED,

        /**
         * Uses the integrated uuid fetcher from spigot/bungeecord to resolved UUIDs.<br>
         * <b>Recommended for:</b> None (should not be used as a default setting /
         * maybe useful to avoid exceeding API rate limits.)
         */
        INTERN,

        /**
         * Tries to resolve the UUID using the {@link #INTERN} fetcher and uses the
         * {@link #RESTFUL} fetcher as a fallback.<br>
         * <b>Recommended for:</b> Spigot &amp; Bungeecord Servers running in online mode.
         */
        MIXED,

        /**
         * Resolves the UUID using the REST-Services configured in the config.yml.
         * <b>Recommended for:</b> Servers in offline mode which still try to keep track of name changes.
         */
        RESTFUL
    }
}