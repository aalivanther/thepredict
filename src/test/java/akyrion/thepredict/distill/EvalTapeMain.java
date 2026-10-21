package akyrion.thepredict.distill;

/** Champion tape entry. A paid flag is refused and nothing is called. */
public final class EvalTapeMain {
    private EvalTapeMain() {
    }

    /**
     * Refuses {@code -PliveJev=true}.
     *
     * @param arguments ignored
     */
    public static void main(String[] arguments) {
        if ("true".equals(System.getProperty("thepredict.liveJev"))) {
            throw new IllegalStateException("evalTape refuses -PliveJev=true");
        }
        System.out.println("evalTape not executed");
    }
}
