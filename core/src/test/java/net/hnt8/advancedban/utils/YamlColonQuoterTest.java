package net.hnt8.advancedban.utils;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlColonQuoterTest {

    @Test
    void quotesTrailingColonChannelPrefix() {
        assertEquals("    LinkPrefix: 'avesban:link:'",
                YamlColonQuoter.quoteLine("    LinkPrefix: avesban:link:"));
        assertEquals("    ProxyIn: 'avesban:proxy:in'",
                YamlColonQuoter.quoteLine("    ProxyIn: avesban:proxy:in"));
    }

    @Test
    void leavesAlreadyQuotedAndNestedKeysAlone() {
        assertEquals("    LinkPrefix: 'avesban:link:'",
                YamlColonQuoter.quoteLine("    LinkPrefix: 'avesban:link:'"));
        assertEquals("Redis:", YamlColonQuoter.quoteLine("Redis:"));
        assertEquals("  Enabled: false", YamlColonQuoter.quoteLine("  Enabled: false"));
    }

    @Test
    void rewrittenFileLoadsInSnakeYaml() throws Exception {
        File file = File.createTempFile("avesban-velocity-config", ".yml");
        file.deleteOnExit();
        Files.write(file.toPath(), (
                "Redis:\n" +
                "  Channels:\n" +
                "    LinkPrefix: avesban:link:\n" +
                "    ProxyIn: avesban:proxy:in\n"
        ).getBytes(StandardCharsets.UTF_8));

        assertTrue(YamlColonQuoter.quoteUnquotedColonValues(file, null));
        assertFalse(YamlColonQuoter.quoteUnquotedColonValues(file, null));

        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) yaml.load(
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) root.get("Redis");
        @SuppressWarnings("unchecked")
        Map<String, Object> channels = (Map<String, Object>) redis.get("Channels");
        assertEquals("avesban:link:", channels.get("LinkPrefix"));
        assertEquals("avesban:proxy:in", channels.get("ProxyIn"));
    }
}
