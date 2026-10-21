package akyrion.thepredict;

/** Documents and checks the Java runtime required by the Paper 26.2 build. */
final class JavaCompatibility {
    /** Minimum Java feature release used to compile and run this addon. */
    public static final int MINIMUM_FEATURE = 25;

    /** Creates the runtime compatibility check. */
    public JavaCompatibility() {
    }

    /** Checks the feature version reported by the current JVM.
     * @param feature Java runtime feature version
     * @return whether the JVM satisfies this build's requirement
     */
    public boolean supports(int feature) {
        return feature >= MINIMUM_FEATURE;
    }

    /** Returns a clear result for the currently running JVM.
     * @return current Java feature version
     * @throws IllegalStateException when this JVM is too old
     */
    public int requireCompatibleRuntime() {
        var feature = Runtime.version().feature();
        if (!supports(feature)) {
            throw new IllegalStateException("Java " + feature + " is unsupported; ThePredict requires Java " + MINIMUM_FEATURE + " or newer");
        }
        return feature;
    }
}
