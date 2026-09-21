package net.hnt8.advancedban.utils;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    @Test
    void appendsMissingTopLevelSectionWithoutTouchingExistingValues() throws Exception {
        File file = File.createTempFile("avesban-config", ".yml");
        file.deleteOnExit();
        Files.write(file.toPath(), (
                "# keep me\n" +
                "UseMySQL: true\n" +
                "Debug: true\n"
        ).getBytes(StandardCharsets.UTF_8));

        String defaults = (
                "UseMySQL: false\n" +
                "Debug: false\n" +
                "\n" +
                "# Redis pub/sub\n" +
                "Redis:\n" +
                "  Enabled: false\n" +
                "  Host: 127.0.0.1\n"
        );

        List<String> added = ConfigMigrator.mergeMissingKeys(file,
                new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)), null);

        String result = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(added.contains("Redis"));
        assertTrue(result.contains("# keep me"));
        assertTrue(result.contains("UseMySQL: true"));
        assertTrue(result.contains("Debug: true"));
        assertTrue(result.contains("# Redis pub/sub"));
        assertTrue(result.contains("Host: 127.0.0.1"));
        assertFalse(result.contains("UseMySQL: false"));
    }

    @Test
    void addsRedisFromDefaultsEvenWhenDefaultLinkPrefixIsUnquoted() throws Exception {
        File file = File.createTempFile("avesban-old-config", ".yml");
        file.deleteOnExit();
        Files.write(file.toPath(), "UseMySQL: true\nDebug: false\n".getBytes(StandardCharsets.UTF_8));

        String defaults = (
                "UseMySQL: false\n" +
                "Redis:\n" +
                "  Enabled: false\n" +
                "  Channels:\n" +
                "    LinkPrefix: avesban:link:\n" +
                "    ProxyIn: avesban:proxy:in\n" +
                "VoiceMute:\n" +
                "  Enabled: true\n"
        );

        List<String> added = ConfigMigrator.mergeMissingKeys(file,
                new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)), null);

        assertTrue(added.contains("Redis"));
        assertTrue(added.contains("VoiceMute"));
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> loaded = (Map<String, Object>) yaml.load(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(true, loaded.get("UseMySQL"));
        assertTrue(loaded.containsKey("Redis"));
        assertTrue(loaded.containsKey("VoiceMute"));
    }

    @Test
    void insertsMissingNestedKeyUnderExistingSection() throws Exception {
        File file = File.createTempFile("avesban-config", ".yml");
        file.deleteOnExit();
        Files.write(file.toPath(), (
                "Redis:\n" +
                "  Enabled: true\n" +
                "  Host: redis.internal\n"
        ).getBytes(StandardCharsets.UTF_8));

        String defaults = (
                "Redis:\n" +
                "  Enabled: false\n" +
                "  Host: 127.0.0.1\n" +
                "  AuthToken: change-me\n" +
                "  Port: 6379\n"
        );

        List<String> added = ConfigMigrator.mergeMissingKeys(file,
                new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)), null);

        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> loaded = (Map<String, Object>) yaml.load(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) loaded.get("Redis");

        assertTrue(added.contains("Redis.AuthToken"));
        assertTrue(added.contains("Redis.Port"));
        assertEquals(true, redis.get("Enabled"));
        assertEquals("redis.internal", redis.get("Host"));
        assertEquals("change-me", String.valueOf(redis.get("AuthToken")));
        assertEquals(6379, ((Number) redis.get("Port")).intValue());
    }
}
