package work.multithreaded;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ThreadsComparison {
    private static final int THREAD_COUNT = 8000;
    private static final int SLEEP_MILLIS = 200;

     static void main(String[] args) throws Exception {

        System.out.println("Running " + THREAD_COUNT + " virtual threads...");
        runThreads(ThreadKind.VIRTUAL);

        System.out.println("\nRunning " + THREAD_COUNT + " platform threads...");
        runThreads(ThreadKind.PLATFORM);
    }

    private static void runThreads(ThreadKind kind) throws InterruptedException {

        // Run garbage collector and wait a bit to minimize noise
        runGC();

        long beforeMem = usedMemory();
        Instant start = Instant.now();

        List<Thread> threads = new ArrayList<>(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread t;
            if (kind == ThreadKind.VIRTUAL) {
                t = Thread.ofVirtual().unstarted(() -> sleep());
            } else {
                t = Thread.ofPlatform().unstarted(() -> sleep());
            }
            threads.add(t);
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }

        Instant end = Instant.now();
        long afterMem = usedMemory();

        long elapsedMs = Duration.between(start, end).toMillis();
        long memUsedBytes = afterMem - beforeMem;
        double memUsedMB = memUsedBytes / (1024.0 * 1024.0);

        System.out.printf("Type: %s, Time elapsed: %d ms, Approx memory used: %.2f MB\n",
                          kind, elapsedMs, memUsedMB);
    }

    private static void sleep() {
        try {
            Thread.sleep(SLEEP_MILLIS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runGC() throws InterruptedException {
        System.gc();
        Thread.sleep(200);
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    enum ThreadKind {
        VIRTUAL, PLATFORM
    }
}
