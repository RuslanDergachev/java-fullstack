package work;

import work.multithreaded.Bank;
import work.multithreaded.LargeShortArray;
import work.multithreaded.PaymentSystem;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static void main(String[] args) throws InterruptedException, ExecutionException {

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

        Bank bank = new Bank(2000, 0L, 1_000L);

        System.out.println("Initial total: " + bank.getSumOfAllAccounts());

        int numThreads = 1000;
        CountDownLatch latch = new CountDownLatch(numThreads);
        Random random = new Random();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        int from = bank.pickRandomAccountId();
                        bank.setAccountBalance(from, bank.getAccountBalance(from));
                        int to;
                        do {
                            to = bank.pickRandomAccountId();
                        } while (to == from);

                        long fromBalance = bank.getAccountBalance(from);

                        if (fromBalance > 0) {
                            long amount = (long) (random.nextDouble() * fromBalance);
                            bank.transfer(from, to, amount);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        System.out.println("Final total: " + bank.getSumOfAllAccounts());


        PaymentSystem ps = new PaymentSystem(200, 1000L, 2000L);

        System.out.println("Initial total: " + ps.getTotal());

        int threadCount = 1000;
        int operationsPerThread = 1000;
        CountDownLatch countLatch = new CountDownLatch(threadCount);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    for (int op = 0; op < operationsPerThread; op++) {
                        int from = ps.pickRandomAccount();
                        int to;
                        do {
                            to = ps.pickRandomAccount();
                        } while (from == to);

                        long bal = ps.getAccountBalance(from);
                        if (bal > 0) {
                            long x = ThreadLocalRandom.current().nextLong(1, bal + 1);
                            ps.transferAtomic(from, to, x);
                        }
                    }
                    countLatch.countDown();
                });
            }
            countLatch.await();
        }

        System.out.println("Final total:   " + ps.getTotal());
    }
}
