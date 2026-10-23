package dev.cyclone.cloak.probe;

/** Process-wide publication generation; begin and final writes share the same lock. */
final class CaptureCoordinator {
    interface Commit { void run() throws Exception; }
    private long generation;
    synchronized long begin() { return ++generation; }
    synchronized boolean current(long token) { return generation == token; }
    synchronized void cancel(long token) { if (generation == token) generation++; }
    synchronized boolean commit(long token, Commit action) throws Exception {
        if (generation != token) return false;
        action.run();
        return true;
    }
}
