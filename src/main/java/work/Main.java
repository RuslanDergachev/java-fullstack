package work;

import work.multithreaded.LargeShortArray;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {
    static void main(String[] args) throws InterruptedException, ExecutionException {

        int size = 100_000_000;
        short[] data = new short[size];
        for (int i = 0; i < size; i++) {
            data[i] = (short) (i % Short.MAX_VALUE);
        }

        LargeShortArray psa = new LargeShortArray(data);

        int[] threadCounts = {1, 10, 100, 1000};
        String outputFile = "src/test/java/results.txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            writer.write("ThreadCount,Method,TimeMs\n");

            for (int threads : threadCounts) {
                // Manual Threads method
                long start = System.nanoTime();
                try {
                    long sumManual = psa.sumWithParallelStream(threads);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    writer.write(String.format("%d,sumWithManualThreads,%d\n", threads, elapsedMs));
                    writer.flush();
                    System.out.printf("Threads=%d manual sum=%,d time=%dms\n", threads, sumManual, elapsedMs);
                } catch (Exception e) {
                    System.err.println("Error in sumWithManualThreads with threads=" + threads);
                    e.printStackTrace();
                }

                start = System.nanoTime();
                try {
                    long sumParallel = psa.sumWithParallelStream(threads);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    writer.write(String.format("%d,sumWithParallelStream,%d\n", threads, elapsedMs));
                    writer.flush();
                    System.out.printf("Threads=%d parallel sum=%,d time=%dms\n", threads, sumParallel, elapsedMs);
                } catch (Exception e) {
                    System.err.println("Error in sumWithParallelStream with threads=" + threads);
                    e.printStackTrace();
                }
            }

            System.out.println("Results written to " + outputFile);

        } catch (IOException ioe) {
            System.err.println("Error writing results file: " + ioe.getMessage());
        }

    }
}