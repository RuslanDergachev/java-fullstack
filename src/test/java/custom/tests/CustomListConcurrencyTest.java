package custom.tests;

import org.junit.jupiter.api.RepeatedTest;
import work.collectionclasses.CustomList;
import work.customannotatition.Test;
import work.multithreaded.ReadWriteLockListDecorator;
import work.multithreaded.SynchronizedListDecorator;

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

        System.out.println("Plain size observed: " + plainCount);
    }

    @Test
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

    @Test(description = "Compact summary: avg time, ratios, throughput")
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
