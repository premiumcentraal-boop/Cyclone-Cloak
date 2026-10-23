package dev.cyclone.cloak.probe;

import java.io.IOException;
import java.util.concurrent.*;

/** Shared bounded executor: interrupted native reads cannot grow threads without limit. */
final class ProbeReadDeadline {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(0, 4, 30,
        TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "cloak-api-reader"); thread.setDaemon(true); return thread;
        });
    private ProbeReadDeadline() { }
    static <T> T call(Callable<T> reader) throws Exception { return call(reader, 3000); }
    static <T> T call(Callable<T> reader, long budgetMs) throws Exception {
        if (budgetMs <= 0 || budgetMs > 3000) throw new IllegalArgumentException("INVALID_READ_BUDGET");
        Future<T> work = null;
        try {
            work = WORKERS.submit(reader);
            return work.get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) { throw new ReadFailure("READ_TIMEOUT"); }
        catch (RejectedExecutionException busy) { throw new ReadFailure("READER_CAPACITY_UNAVAILABLE"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ReadFailure("READ_INTERRUPTED");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof LinkageError) throw (LinkageError) cause;
            throw new ReadFailure("READER_EXCEPTION");
        } finally { if (work != null) work.cancel(true); }
    }
    static final class ReadFailure extends IOException {
        final String reason;
        ReadFailure(String reason) { this.reason = reason; }
    }
}
