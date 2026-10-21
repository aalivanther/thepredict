package akyrion.thepredict.config;

import akyrion.thepredict.domain.AnswerCacheMode;
import akyrion.thepredict.domain.RecordBodies;
import akyrion.thepredict.domain.RecordLevel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageSettingsTest {
    @Test
    void defaultsMatchTheTableAndMysqlIsReserved() {
        var standard = StorageSettings.standard();
        assertEquals(RecordLevel.DECISIONS, standard.level());
        assertEquals(RecordBodies.HASH, standard.bodies());
        assertEquals("records", standard.directory());
        assertEquals(16384, standard.queueCapacity());
        assertEquals("sqlite", standard.aggregates());
        assertEquals("aggregates.db", standard.file());
        assertEquals(AnswerCacheMode.OFF, standard.answerCache());
        assertEquals(0.1, standard.answerCacheRevalidateRatio(), 0);
        var empty = new YamlConfiguration();
        assertEquals(standard, StorageSettings.from(empty));
        var mysql = new YamlConfiguration();
        mysql.set("storage.aggregates", "mysql");
        var error = assertThrows(ConfigurationException.class, () -> StorageSettings.from(mysql));
        assertTrue(error.getMessage().contains("storage.aggregates"));
        assertTrue(error.getMessage().contains("reserved"));
        var low = new YamlConfiguration();
        low.set("records.queueCapacity", 10);
        assertThrows(ConfigurationException.class, () -> StorageSettings.from(low));
        var outside = new YamlConfiguration();
        outside.set("records.directory", "..");
        assertThrows(ConfigurationException.class, () -> StorageSettings.from(outside));
    }
}
