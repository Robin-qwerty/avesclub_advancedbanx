package net.hnt8.advancedban.manager;

import net.hnt8.advancedban.MethodInterface;
import net.hnt8.advancedban.Universal;
import net.hnt8.advancedban.utils.ConfigMigrator;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


/**
 * The Update Manager used to keep config files up to date and migrate them seamlessly to the newest version.
 */
public class UpdateManager {

    private static UpdateManager instance = null;

    /**
     * Get the update manager.
     *
     * @return the update manager instance
     */
    public static synchronized UpdateManager get() {
        return instance == null ? instance = new UpdateManager() : instance;
    }

    /**
     * Initially checks which configuration options from the newest version are missing and tries to add them
     * without altering any old configuration settings.
     */
    public void setup() {
        MethodInterface mi = Universal.get().getMethods();

        if (mi.isUnitTesting()) {
            return;
        }

        migrate("config.yml", new File(mi.getDataFolder(), "config.yml"), mi);
        mi.loadFiles();
    }

    private void migrate(String resourceName, File diskFile, MethodInterface mi) {
        InputStream defaults = UpdateManager.class.getClassLoader().getResourceAsStream(resourceName);
        if (defaults == null) {
            mi.getLogger().warning("Could not find default " + resourceName + " in the plugin jar; skipping config merge.");
            return;
        }
        try {
            List<String> added = ConfigMigrator.mergeMissingKeys(diskFile, defaults, mi.getLogger());
            if (!added.isEmpty()) {
                mi.getLogger().info("Updated " + resourceName + " with " + added.size() + " missing key(s). Existing values were kept.");
            }
        } catch (Exception ex) {
            mi.getLogger().warning("Could not merge missing keys into " + resourceName + ": " + ex.getMessage());
        } finally {
            try {
                defaults.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void addMessage(String search, String insert, int indexOffset) {
        try {
            File file = new File(Universal.get().getMethods().getDataFolder(), "Messages.yml");
            List<String> lines = FileUtils.readLines(file, "UTF8");
            int index = lines.indexOf(search);
            lines.add(index + indexOffset, insert);
            FileUtils.writeLines(file, "UTF8", lines);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
