package net.hnt8.advancedban.backendlink;

import net.hnt8.advancedban.utils.ConfigMigrator;
import net.hnt8.advancedban.utils.YamlColonQuoter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Creates plugins/Avesban-BackendLink/config.yml and fills in any keys missing from the jar defaults.
 * Kept free of Redis/Jedis types so a Redis class-load failure cannot skip config generation.
 */
final class BackendLinkConfigFiles {

    private BackendLinkConfigFiles() {
    }

    static File ensure(JavaPlugin plugin) {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Could not create data folder: " + folder.getAbsolutePath());
        }
        File configFile = new File(folder, "config.yml");
        byte[] defaults = readDefaultBytes(plugin);
        if (!configFile.exists()) {
            try {
                Files.write(configFile.toPath(), defaults);
                plugin.getLogger().info("Created config.yml at " + configFile.getAbsolutePath());
            } catch (IOException ex) {
                plugin.getLogger().severe("Failed to write " + configFile.getAbsolutePath() + ": " + ex.getMessage());
                return configFile;
            }
        }
        try {
            YamlColonQuoter.quoteUnquotedColonValues(configFile, plugin.getLogger());
            List<String> added = ConfigMigrator.mergeMissingKeys(
                    configFile, new ByteArrayInputStream(defaults), plugin.getLogger());
            if (!added.isEmpty()) {
                plugin.getLogger().info("Added missing config keys " + added + " in " + configFile.getAbsolutePath());
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not merge missing config keys (file was still created): " + ex.getMessage());
        }
        plugin.getLogger().info("BackendLink config: " + configFile.getAbsolutePath());
        return configFile;
    }

    private static byte[] readDefaultBytes(JavaPlugin plugin) {
        InputStream in = plugin.getResource("config.yml");
        if (in == null) {
            in = BackendLinkConfigFiles.class.getClassLoader().getResourceAsStream("config.yml");
        }
        if (in != null) {
            try (InputStream stream = in) {
                return readAll(stream);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not read jar config.yml, using built-in defaults: " + ex.getMessage());
            }
        } else {
            plugin.getLogger().warning("Jar did not contain config.yml; writing built-in defaults.");
        }
        return FALLBACK.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        byte[] buffer = new byte[4096];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static final String FALLBACK =
            "# Avesban BackendLink\n"
                    + "LinkName: survival\n"
                    + "\n"
                    + "Redis:\n"
                    + "  Enabled: false\n"
                    + "  Host: 127.0.0.1\n"
                    + "  Port: 6379\n"
                    + "  Password: ''\n"
                    + "  Database: 0\n"
                    + "  AuthToken: 'change-me'\n"
                    + "  MaxAgeMs: 60000\n"
                    + "  Channels:\n"
                    + "    ProxyIn: 'avesban:proxy:in'\n"
                    + "    LinkAcks: 'avesban:link:acks'\n"
                    + "    LinkPrefix: 'avesban:link:'\n"
                    + "\n"
                    + "VoiceMute:\n"
                    + "  Enabled: true\n";
}
