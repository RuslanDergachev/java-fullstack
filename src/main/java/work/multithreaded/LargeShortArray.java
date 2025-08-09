package work.multithreaded;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class LargeShortArray {
    private final short[] array;

    public LargeShortArray(short[] array) {
        this.array = array;
    }

    public long sumWithParallelStream(int threadsCount) throws ExecutionException, InterruptedException {
        ForkJoinPool customThreadPool = new ForkJoinPool(threadsCount);
        try {
            return customThreadPool.submit(
                    () -> IntStream.range(0, array.length).parallel().mapToLong(i -> array[i]).sum()).get();
        } catch (Exception e) {
            throw new RuntimeException("Error during parallel sum", e);
        } finally {
            customThreadPool.shutdown();
        }
    }
}

