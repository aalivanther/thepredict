package akyrion.thepredict.domain.distill;

import java.util.List;

/**
 * Ordered regime rules (M29.5). The first match wins. The last rule is always {@code r1:ALL}.
 *
 * @param rules the rules, at most {@code distill.maxRegimesPerProfile}
 */
public record RegimeManifest(List<Rule> rules) {
    /**
     * One rule.
     *
     * @param enemyMask bits of {@link EnemyBand#ordinal()}
     * @param phaseMask bits of {@link FightPhase#ordinal()}
     * @param key regime key
     * @param artifactSha256 distilled artifact
     */
    public record Rule(int enemyMask, int phaseMask, String key, String artifactSha256) {
    }

    /**
     * Copies the rules and requires a final {@code ALL}.
     */
    public RegimeManifest {
        rules = List.copyOf(rules);
        if (rules.isEmpty() || !"r1:ALL".equals(rules.getLast().key())) {
            throw new IllegalArgumentException("regime manifest");
        }
    }

    /**
     * Whether the manifest respects the cap.
     *
     * @param cap {@code distill.maxRegimesPerProfile}
     * @return {@code true} when the rule count is inside the cap
     */
    public boolean within(int cap) {
        return rules.size() <= cap;
    }

    /**
     * The first rule whose masks contain the bands.
     *
     * @param band enemy band
     * @param phase fight phase
     * @return the key
     */
    public String match(EnemyBand band, FightPhase phase) {
        var enemy = 1 << band.ordinal();
        var phaseBit = 1 << phase.ordinal();
        for (var rule : rules) {
            if ((rule.enemyMask() & enemy) != 0 && (rule.phaseMask() & phaseBit) != 0) {
                return rule.key();
            }
        }
        return RegimeKey.ALL.text();
    }
}
