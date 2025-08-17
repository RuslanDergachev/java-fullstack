package custom.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import work.collectionclasses.CustomList;
import work.customannotatition.Test;
import work.multithreaded.ReadWriteLockListDecorator;
import work.multithreaded.SynchronizedListDecorator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomListConcurrencyTest {
    private static final int PER_THREAD = 1_000_000;

    @RepeatedTest(100)
    void correctness_plain_vs_synchronized_vs_rwlock() throws Exception {
        List<Integer> plain = new CustomList<>();
        int plainCount = runTwoThreadsAdd(plain);

        List<Integer> sync = new SynchronizedListDecorator<>(new CustomList<>());
        int syncCount = runTwoThreadsAdd(sync);

        List<Integer> rw = new ReadWriteLockListDecorator<>(new CustomList<>());
        int rwCount = runTwoThreadsAdd(rw);

        assertThat(syncCount).isEqualTo(PER_THREAD * 2);
        assertThat(rwCount).isEqualTo(PER_THREAD * 2);

        int expected = PER_THREAD * 2;
        if (plainCount != expected) {
            System.out.printf("[correctness] plain=%d (lost=%d), syncOK=%b, rwOK=%b%n",
                              plainCount, (expected - plainCount),
                              syncCount == expected, rwCount == expected);
        }
    }

    @Test
    @DisplayName("performance: single run (ms)")
    void performance_plain_vs_synchronized_vs_rwlock() throws Exception {
        long tPlain = timeMillis(() -> runTwoThreadsAdd(new CustomList<>()));
        long tSync  = timeMillis(() -> runTwoThreadsAdd(new SynchronizedListDecorator<>(new CustomList<>())));
        long tRw    = timeMillis(() -> runTwoThreadsAdd(new ReadWriteLockListDecorator<>(new CustomList<>())));

        System.out.printf("Performance (ms): plain=%d, synchronized=%d, rwlock=%d%n", tPlain, tSync, tRw);

        List<Integer> sync = new SynchronizedListDecorator<>(new CustomList<>());
        List<Integer> rw = new ReadWriteLockListDecorator<>(new CustomList<>());
        assertThat(runTwoThreadsAdd(sync)).isEqualTo(PER_THREAD * 2);
        assertThat(runTwoThreadsAdd(rw)).isEqualTo(PER_THREAD * 2);
    }

    @Test
    @DisplayName("performance: avg over 5 runs (compact)")
    void performance_summary_compact() throws Exception {
        final int runs = 5;
        final long ops = PER_THREAD * 2L;

        long plainMs = avgMillis(runs, () -> runTwoThreadsAdd(new CustomList<>()));
        long syncMs  = avgMillis(runs, () -> runTwoThreadsAdd(new SynchronizedListDecorator<>(new CustomList<>())));
        long rwMs    = avgMillis(runs, () -> runTwoThreadsAdd(new ReadWriteLockListDecorator<>(new CustomList<>())));

        double syncVsPlain = plainMs == 0 ? Double.NaN : (syncMs * 1.0 / plainMs);
        double rwVsPlain   = plainMs == 0 ? Double.NaN : (rwMs   * 1.0 / plainMs);
        double rwVsSync    = syncMs  == 0 ? Double.NaN : (rwMs   * 1.0 / syncMs);

        double plainRps = plainMs == 0 ? 0 : ops / (plainMs / 1000.0);
        double syncRps  = syncMs  == 0 ? 0 : ops / (syncMs  / 1000.0);
        double rwRps    = rwMs    == 0 ? 0 : ops / (rwMs    / 1000.0);

        System.out.println("=== Compact Summary (avg over " + runs + " runs) ===");
        System.out.printf("Time (ms): plain=%d, sync=%d, rw=%d%n", plainMs, syncMs, rwMs);
        System.out.printf("Speed vs plain: sync=%.2fx, rw=%.2fx; rw vs sync=%.2fx%n",
                          syncVsPlain, rwVsPlain, rwVsSync);
        System.out.printf("Throughput (ops/s): plain=%.0f, sync=%.0f, rw=%.0f%n",
                          plainRps, syncRps, rwRps);
        System.out.println("(ops = " + ops + " adds per run)");
    }

    @Test
    @DisplayName("correctness: 100 runs diff and summary")
    void correctness_diff_over_100() throws Exception {
        final int runs = 100;
        final int expected = PER_THREAD * 2;

        List<Integer> results = new ArrayList<>(runs);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int ok = 0;

        System.out.println("=== Plain correctness over " + runs + " runs ===");
        for (int i = 1; i <= runs; i++) {
            int plain = runTwoThreadsAdd(new CustomList<>());
            int lost = expected - plain;

            results.add(plain);
            min = Math.min(min, plain);
            max = Math.max(max, plain);
            sum += plain;
            if (plain == expected) ok++;

            System.out.printf("run #%03d: plain=%d (lost=%d)%n", i, plain, lost);
        }

        results.sort(Integer::compareTo);
        double median = (runs % 2 == 1)
                ? results.get(runs / 2)
                : (results.get(runs / 2 - 1) + results.get(runs / 2)) / 2.0;

        long avg = Math.round(sum / (double) runs);
        int minLost = expected - min;
        int maxLost = expected - max;
        long avgLost = expected - avg;
        double okPct = ok * 100.0 / runs;

        System.out.printf("=== Summary x%d ===%n", runs);
        System.out.printf("min=%d (lost=%d), max=%d (lost=%d), avg=%d (lost=%.0f), median=%.0f, ok=%d (%.1f%%)%n",
                          min, minLost, max, maxLost, avg, (double) avgLost, median, ok, okPct);
    }

    private static long avgMillis(int runs, IoRunnable r) throws Exception {
        long sum = 0;
        for (int i = 0; i < runs; i++) sum += timeMillis(r);
        return sum / runs;
    }

    private static int runTwoThreadsAdd(List<Integer> list) throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < PER_THREAD; i++) list.add(i);
                } finally { latch.countDown(); }
            });
            executor.submit(() -> {
                try {
                    for (int i = 0; i < PER_THREAD; i++) list.add(i);
                } finally { latch.countDown(); }
            });
            if (!latch.await(2, java.util.concurrent.TimeUnit.MINUTES)) {
                throw new RuntimeException("Timeout waiting for threads");
            }
        }
        return list.size();
    }

    private static long timeMillis(IoRunnable r) throws Exception {
        long start = System.nanoTime();
        r.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @FunctionalInterface
    interface IoRunnable {
        void run() throws Exception;
    }
}
