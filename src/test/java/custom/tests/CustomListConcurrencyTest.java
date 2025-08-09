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
