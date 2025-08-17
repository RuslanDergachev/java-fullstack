package work.multithreaded;

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class LargeShortArray {
    private final short[] array;

    public LargeShortArray(short[] array) {
        this.array = Objects.requireNonNull(array, "array");
    }

    public long sumWithParallelStream(int threadsCount) {
        if (array.length == 0) return 0L;

        int n = Math.max(1, threadsCount);
        ForkJoinPool pool = new ForkJoinPool(n);
        try {
            return pool.submit(() ->
                                       IntStream.range(0, array.length)
                                               .parallel()
                                               .mapToLong(i -> array[i])
                                               .sum()
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Error during parallel stream sum", e);
        } finally {
            pool.shutdown();
        }
    }

    public long sumWithParallelThreads(int threadsCount) {
        if (array.length == 0) return 0L;

        int n = Math.max(1, Math.min(threadsCount, array.length));
        long[] partial = new long[n];
        Thread[] threads = new Thread[n];

        int base = array.length / n;
        int rem = array.length % n;

        int from = 0;
        for (int t = 0; t < n; t++) {
            int size = base + (t < rem ? 1 : 0);
            int start = from;
            int end = start + size;
            int idx = t;

            threads[t] = Thread.ofPlatform().unstarted(() -> {
                long s = 0L;
                for (int i = start; i < end; i++) s += array[i];
                partial[idx] = s;
            });
            from = end;
        }

        for (Thread th : threads) th.start();
        for (Thread th : threads) {
            try {
                th.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while joining threads", ie);
            }
        }

        long total = 0L;
        for (long p : partial) total += p;
        return total;
    }
}

