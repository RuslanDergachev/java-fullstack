package custom.tests;

import org.junit.jupiter.api.Assertions;
import work.FibonacciAlgorithms;
import work.customannotatition.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FibonacciAlgorithmsTest {
    private FibonacciAlgorithms fibAlgo;

    // 1. Correctness Test: verify all implementations produce identical results for inputs 0-35
    @Test
    public void correctnessTest() {
        fibAlgo = new FibonacciAlgorithms();
        for (int n = 0; n <= 35; n++) {
            long rec = FibonacciAlgorithms.fibonacciRecursive(n);
            long memo = fibAlgo.fibonacciMemo(n);
            long iter = FibonacciAlgorithms.fibonacciIterative(n);

            assertEquals(rec, memo, "Mismatch at n=" + n + " between recursive and memoized");
            assertEquals(rec, iter, "Mismatch at n=" + n + " between recursive and iterative");
        }
    }

    // Helper class to hold results for report
    static class PerfResult {
        int n;
        long recursiveTimeMs;
        long memoizedTimeMs;
        long iterativeTimeMs;
        long recursiveMemoryBytes;
        long memoizedMemoryBytes;
        long iterativeMemoryBytes;
    }

    // 2 & 3. Performance Benchmark + Memory Usage Analysis
    @Test
    public void performanceAndMemoryTest() {
        fibAlgo = new FibonacciAlgorithms();
        int[] testInputs = {10, 20, 30, 35, 40, 45};
        List<PerfResult> results = new ArrayList<>();

        for (int n : testInputs) {
            PerfResult r = new PerfResult();
            r.n = n;

            // Recursive
            System.gc();
            sleep(100);
            long memBefore = usedMemory();
            long start = System.currentTimeMillis();
            long valRec = FibonacciAlgorithms.fibonacciRecursive(n);
            long end = System.currentTimeMillis();
            long memAfter = usedMemory();
            r.recursiveTimeMs = end - start;
            r.recursiveMemoryBytes = memAfter - memBefore;

            // Memoized
            System.gc();
            sleep(100);
            memBefore = usedMemory();
            start = System.currentTimeMillis();
            long valMemo = fibAlgo.fibonacciMemo(n);
            end = System.currentTimeMillis();
            memAfter = usedMemory();
            r.memoizedTimeMs = end - start;
            r.memoizedMemoryBytes = memAfter - memBefore;

            // Iterative
            System.gc();
            sleep(100);
            memBefore = usedMemory();
            start = System.currentTimeMillis();
            long valIter = FibonacciAlgorithms.fibonacciIterative(n);
            end = System.currentTimeMillis();
            memAfter = usedMemory();
            r.iterativeTimeMs = end - start;
            r.iterativeMemoryBytes = memAfter - memBefore;

            // Verify correctness
            Assertions.assertEquals(valRec, valMemo, "Values differ for n=" + n + " between recursive and memoized");
            Assertions.assertEquals(valRec, valIter, "Values differ for n=" + n + " between recursive and iterative");

            results.add(r);
        }

        printReport(results);
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private void printReport(List<PerfResult> results) {
        System.out.println("Fibonacci Performance Report:");
        System.out.printf("%-5s %-18s %-18s %-18s %-20s %-20s %-20s%n",
                          "n", "Recursive Time(ms)", "Memoized Time(ms)", "Iterative Time(ms)",
                          "Recursive Mem(bytes)", "Memoized Mem(bytes)", "Iterative Mem(bytes)");
        for (PerfResult r : results) {
            System.out.printf("%-5d %-18d %-18d %-18d %-20d %-20d %-20d%n",
                              r.n,
                              r.recursiveTimeMs,
                              r.memoizedTimeMs,
                              r.iterativeTimeMs,
                              r.recursiveMemoryBytes,
                              r.memoizedMemoryBytes,
                              r.iterativeMemoryBytes);
        }

        System.out.println("\nTime Complexity Analysis based on measurements:");
        System.out.println("- Recursive: Exponential growth in time; becomes impractical near n=40-45.");
        System.out.println("- Memoized: Linear time; efficiently handles larger inputs.");
        System.out.println("- Iterative: Fastest with minimal memory usage; linear time and constant space.");
    }
}
