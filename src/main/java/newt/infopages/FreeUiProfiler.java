package newt.infopages;

/** Timing hook retained as a no-op in production builds. */
final class FreeUiProfiler {
    private FreeUiProfiler() {}
    static void start() {}
    static void end(String command) {}
}
