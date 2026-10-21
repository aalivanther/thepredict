package akyrion.thepredict.eval;

/**
 * Marks a server-record comparison (M27.11). Session 27 has not been run, so the ratio against the simulator is absent.
 */
public final class RecordsMemoryReport {
    private RecordsMemoryReport() {
    }

    /**
     * Writes the same known case and records that the server ratio is pending.
     *
     * @param arguments the report directory
     * @throws Exception when the report cannot be written
     */
    public static void main(String[] arguments) throws Exception {
        MemoryEval.main(arguments.length == 0 ? new String[] {"report"} : arguments);
    }
}
