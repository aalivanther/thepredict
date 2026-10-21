package akyrion.thepredict.adapter.jev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seed bank of M13.6 stays what the encoder writes today, holds only aliases, and labels only offered tactics. A
 * mismatch writes the fresh document to {@code build/scenario-seed/} so it can be reviewed and copied.
 */
class ScenarioSeedBankTest {
    private static final Path BANK = Path.of("src", "test", "resources", "scenarios", "seed");
    private static final Path FROZEN = Path.of("src", "test", "resources", "scenarios", "seed-2026-09-29");
    private static final Map<String, String> FROZEN_SHA256 = Map.of(
            "seed-low-health.json", "027c38928ad7b6a5abc9deea46359ff65b2c9bd6c236c74e248c86d1bdc94e05",
            "seed-melee-duel.json", "50de6350c37befc30e1a32466aae43fd2412f2e37fd08ba2bd0df163a2438383",
            "seed-pillar.json", "c0e0da03f15aa42135a1c1a67fd7a6a7ef0773b5988863eb84dcb8ef57bf8a36",
            "seed-shield.json", "8fe9a9cae873da22bc9fc9492e64603b184d8609756a007624b6a3b80079d699",
            "seed-shooter-backing-away.json", "fd87aade8ae6f05f9f0e763a3e7a79f0f5a1c986c9ad858e064114c32e26ec3c",
            "seed-tank-and-shooter.json", "cc6dc0b4fe087d15882365b71df4241aa4c3a622bc56fa5089ff458e9268eafd");

    @ParameterizedTest
    @EnumSource(ScenarioFacts.Seed.class)
    void theSeedFileIsWhatTheEncoderWritesTodayWithAliasesOnly(ScenarioFacts.Seed seed) throws IOException {
        var expected = ScenarioSeedBank.document(seed);
        var file = BANK.resolve(ScenarioSeedBank.fileName(seed));
        if (!Files.exists(file) || !Files.readString(file).equals(expected)) {
            var fresh = Path.of("build", "scenario-seed", ScenarioSeedBank.fileName(seed));
            Files.createDirectories(fresh.getParent());
            Files.writeString(fresh, expected);
        }
        assertTrue(Files.exists(file), "missing " + file);
        assertEquals(expected, Files.readString(file));
        assertFalse(expected.matches("(?s).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*"),
                "no UUID in a scenario");
        var document = ScenarioDocument.read(seed.name(), expected);
        var label = document.labels().values().iterator().next();
        assertTrue(document.questions().getFirst().options().containsAll(label.acceptable()));
    }

    @Test
    void theFrozenSeedsOfThePaidBankNeverChange() throws Exception {
        try (var listing = Files.list(FROZEN)) {
            assertEquals(FROZEN_SHA256.keySet(), listing.map(file -> file.getFileName().toString())
                    .collect(Collectors.toSet()));
        }
        for (var entry : FROZEN_SHA256.entrySet()) {
            var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(FROZEN.resolve(entry.getKey())));
            assertEquals(entry.getValue(), HexFormat.of().formatHex(digest), entry.getKey());
        }
    }

    @Test
    void theRegeneratedSeedsDescribeTheDeclaredSkillsTheyOfferInTheCompactDetail() throws Exception {
        var criteria = new StringBuilder();
        for (var seed : ScenarioFacts.Seed.values()) {
            criteria.append(Files.readString(BANK.resolve(ScenarioSeedBank.fileName(seed))));
        }
        for (var skill : List.of("melee hit, medium damage", "instant ranged hit, low damage",
                "area hit around self stun or slow")) {
            assertTrue(criteria.toString().contains(skill), skill);
        }
        assertFalse(Files.exists(BANK.resolve("seed-low-health.json")), "replaced by the family lowhp of the bank");
    }
}
