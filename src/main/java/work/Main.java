package work;

import work.multithreaded.LargeShortArray;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    static void main(String[] args) {

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
                long start = System.nanoTime();
                long sumManual = psa.sumWithParallelThreads(threads);
                long elapsedManualMs = (System.nanoTime() - start) / 1_000_000;
                writer.write(String.format("%d,sumWithParallelThreads,%d%n", threads, elapsedManualMs));
                System.out.printf("Threads=%d manual sum=%,d time=%dms%n", threads, sumManual, elapsedManualMs);

                start = System.nanoTime();
                long sumParallel = psa.sumWithParallelStream(threads);
                long elapsedParallelMs = (System.nanoTime() - start) / 1_000_000;
                writer.write(String.format("%d,sumWithParallelStream,%d%n", threads, elapsedParallelMs));
                System.out.printf("Threads=%d parallel sum=%,d time=%dms%n", threads, sumParallel, elapsedParallelMs);
            }

            System.out.println("Results written to " + outputFile);
        } catch (IOException ioe) {
            System.err.println("Error writing results file: " + ioe.getMessage());
        }
    }
}