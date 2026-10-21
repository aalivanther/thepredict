package akyrion.thepredict.adapter.storage;

/**
 * Server-side distill build guard (M29.6). The writer may train from outcome, rollout or Kev. A Jev dagger is
 * refused. This class does not open a connection and does not train.
 */
public final class DistillBuildTask {
    private DistillBuildTask() {
    }

    /**
     * Accepts the same dagger tokens as {@code distillTrain}.
     *
     * @param dagger {@code off}, {@code kev} or {@code rollout}
     */
    public static void requireDagger(String dagger) {
        if (!"off".equals(dagger) && !"kev".equals(dagger) && !"rollout".equals(dagger)) {
            throw new IllegalArgumentException("distillTrain dagger is off, kev or rollout");
        }
    }
}
