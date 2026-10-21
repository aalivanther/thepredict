package akyrion.thepredict.domain;

/**
 * Party size band stored beside a rung (M27). Errata E27-4 applied: no ladder type exists, so this enum maps the
 * strings already carried by {@link DifficultyContext#partyBand(int)}.
 */
public enum PartyBand {
    /** One player. */
    ONE("1"),
    /** Two or three players. */
    TWO_THREE("2_3"),
    /** Four or more players. */
    FOUR_PLUS("4P");

    private final String text;

    PartyBand(String text) {
        this.text = text;
    }

    /**
     * The string stored on a difficulty context.
     *
     * @return {@code 1}, {@code 2_3} or {@code 4P}
     */
    public String text() {
        return text;
    }

    /**
     * Band of a party size.
     *
     * @param partySize players in the party, at least 1
     * @return the band
     */
    public static PartyBand of(int partySize) {
        if (partySize <= 1) {
            return ONE;
        }
        return partySize <= 3 ? TWO_THREE : FOUR_PLUS;
    }
}
