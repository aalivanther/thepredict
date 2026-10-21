package akyrion.thepredict.distill;

import akyrion.thepredict.adapter.storage.DistillBuildTask;

/** Distill build entry. Labels are outcome, rollout or kev. Jev is refused. Training is not run. */
public final class DistillTrainMain {
    private DistillTrainMain() {
    }

    /**
     * Refuses a Jev dagger mode. The check is the same one the storage task uses.
     *
     * @param arguments ignored
     */
    public static void main(String[] arguments) {
        var dagger = System.getProperty("thepredict.dagger", "off");
        DistillBuildTask.requireDagger(dagger);
        System.out.println("distillTrain not executed dagger=" + dagger);
    }
}
