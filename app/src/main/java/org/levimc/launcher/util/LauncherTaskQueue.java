package org.levimc.launcher.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared FIFO queue for long local launcher operations. */
public final class LauncherTaskQueue {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "launcher-task-queue");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger PENDING = new AtomicInteger();

    private LauncherTaskQueue() { }

    public static void submit(Runnable task) {
        submitFuture(task);
    }

    public static java.util.concurrent.Future<?> submitFuture(Runnable task) {
        PENDING.incrementAndGet();
        return EXECUTOR.submit(() -> {
            try {
                task.run();
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    public static ExecutorService executor() { return EXECUTOR; }
    public static int getPendingCount() { return Math.max(0, PENDING.get()); }
}
