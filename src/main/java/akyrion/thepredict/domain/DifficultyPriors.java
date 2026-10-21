package akyrion.thepredict.domain;

/**
 * Opening rung and the one sample a fight may add (M22, M27.5). The live profile loop stays in the session. A
 * subject prior may move the opening rung and still takes at most one step per fight.
 */
public interface DifficultyPriors {
    /**
     * Rung used when the fight opens.
     *
     * @param key profile, section and subject
     * @return the rung
     */
    int startRung(DifficultyKey key);

    /**
     * One closed fight. A death does not raise a stored rung. A group sample comes only from the group clock, which
     * the caller expresses with {@link DifficultySection#GROUP}.
     *
     * @param key the fight key
     * @param summary the close
     */
    void observe(DifficultyKey key, FightSummary summary);
}
