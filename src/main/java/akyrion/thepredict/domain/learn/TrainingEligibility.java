package akyrion.thepredict.domain.learn;

/**
 * Whether a mob may train against a player (M28.5). The key lives on the mob file. There is no admin command,
 * timer or global switch. Absent means false. Invalid is a catalog error reported by the binder.
 */
public final class TrainingEligibility {
    private TrainingEligibility() {
    }

    /**
     * Whether this mob explores while a player is present.
     *
     * @param predictor {@code Predictor: true}
     * @param training {@code PredictorTraining}; absent is false
     * @param playersPresent a player is in the encounter
     * @return {@code true} only for a training mob while a player is present
     */
    public static boolean exploresWithPlayers(boolean predictor, boolean training, boolean playersPresent) {
        return predictor && training && playersPresent;
    }

    /**
     * Whether a present training flag is legal.
     *
     * @param predictor {@code Predictor}
     * @param training the parsed flag
     * @return {@code false} when training is requested without {@code Predictor: true}
     */
    public static boolean legal(boolean predictor, boolean training) {
        return predictor || !training;
    }
}
