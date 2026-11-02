package com.rconfix;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs RCON commands on the server thread. Vanilla runs them on the RCON connection's own thread, where commands
 * that touch the world can corrupt it or crash. {@link RconFix} drains the queue at the start of each server tick.
 */
public final class ServerThreadQueue {

    private static final ConcurrentLinkedQueue<FutureTask<?>> tasks = new ConcurrentLinkedQueue<>();

    private ServerThreadQueue() {}

    /** Queues the call and waits for the server thread to run it. */
    public static <T> T call(Callable<T> callable, long timeoutSeconds)
        throws InterruptedException, ExecutionException, TimeoutException {
        FutureTask<T> task = new FutureTask<>(callable);
        tasks.add(task);
        return task.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Runs every queued call. Server thread only. */
    public static void drain() {
        FutureTask<?> task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    /** Fails calls that are still waiting, for example when the server stops. */
    public static void cancelAll() {
        FutureTask<?> task;
        while ((task = tasks.poll()) != null) {
            task.cancel(false);
        }
    }
}
