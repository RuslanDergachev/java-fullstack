package work.multithreaded;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class BankClientSimulation {

    static void main(String[] args) throws InterruptedException {
        final int accounts = 200;
        final long min = 0L;
        final long max = 1_000L;

        Bank bank = new Bank(accounts, min, max);

        System.out.println("Initial total: " + bank.getSumOfAllAccounts());

        long start, end;

        start = System.nanoTime();
        runWithSynchronized(bank, accounts, 1_000, 1_000);
        end = System.nanoTime();
        BigInteger finalTotalSynchronized = bank.getSumOfAllAccounts();
        System.out.println("Final total synchronized: " + finalTotalSynchronized);
        System.out.println("Totals equal: " + bank.getSumOfAllAccounts().equals(finalTotalSynchronized));
        System.out.println("Time elapsed (synchronized): " + ((end - start) / 1_000_000) + " ms");

        start = System.nanoTime();
        runWithLocks(bank, accounts, 1_000, 1_000);
        end = System.nanoTime();
        BigInteger finalTotalLock = bank.getSumOfAllAccounts();
        System.out.println("Final total with locks: " + finalTotalLock);
        System.out.println("Totals equal: " + bank.getSumOfAllAccounts().equals(finalTotalLock));
        System.out.println("Time elapsed (locks): " + ((end - start) / 1_000_000) + " ms");

        start = System.nanoTime();
        runWithAtomics(accounts, min, max, 1_000, 1_000);
        end = System.nanoTime();
        BigInteger finalTotalAtomic = bank.getSumOfAllAccounts();
        System.out.println("Final total with atomics: " + finalTotalAtomic);
        System.out.println("Totals equal: " + bank.getSumOfAllAccounts().equals(finalTotalAtomic));
        System.out.println("Time elapsed (atomics): " + ((end - start) / 1_000_000) + " ms");
    }

    private static void runWithSynchronized(Bank bank, int accounts, int threads, int opsPerThread) throws InterruptedException {
        System.out.println("\n--- Version A: synchronized ---");
        Object[] locks = new Object[accounts];
        for (int i = 0; i < accounts; i++) locks[i] = new Object();

        CountDownLatch latch = new CountDownLatch(threads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            int from = bank.pickRandomAccountId();
                            int to;
                            do {
                                to = bank.pickRandomAccountId();
                            } while (to == from);

                            int first = Math.min(from, to);
                            int second = Math.max(from, to);

                            synchronized (locks[first]) {
                                synchronized (locks[second]) {
                                    long fromBal = bank.getAccountBalance(from);
                                    if (fromBal <= 0) continue;

                                    long x = ThreadLocalRandom.current().nextLong(1, fromBal + 1);
                                    bank.setAccountBalance(from, fromBal - x);
                                    long toBal = bank.getAccountBalance(to);
                                    long newTo;
                                    try {
                                        newTo = Math.addExact(toBal, x);
                                    } catch (ArithmeticException ex) {
                                        bank.setAccountBalance(from, fromBal);
                                        continue;
                                    }
                                    bank.setAccountBalance(to, newTo);
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
    }

    // ReentrantLock
    private static void runWithLocks(Bank bank, int accounts, int threads, int opsPerThread) throws InterruptedException {
        System.out.println("\n--- Version B: ReentrantLock ---");
        ReentrantLock[] locks = new ReentrantLock[accounts];
        for (int i = 0; i < accounts; i++) locks[i] = new ReentrantLock();

        CountDownLatch latch = new CountDownLatch(threads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            int from = bank.pickRandomAccountId();
                            int to;
                            do {
                                to = bank.pickRandomAccountId();
                            } while (to == from);

                            int first = Math.min(from, to);
                            int second = Math.max(from, to);

                            locks[first].lock();
                            locks[second].lock();
                            try {
                                long fromBal = bank.getAccountBalance(from);
                                if (fromBal <= 0) continue;

                                long x = ThreadLocalRandom.current().nextLong(1, fromBal + 1);
                                bank.setAccountBalance(from, fromBal - x);

                                long toBal = bank.getAccountBalance(to);
                                long newTo;
                                try {
                                    newTo = Math.addExact(toBal, x);
                                } catch (ArithmeticException ex) {
                                    bank.setAccountBalance(from, fromBal); // откат
                                    continue;
                                }
                                bank.setAccountBalance(to, newTo);
                            } finally {
                                locks[second].unlock();
                                locks[first].unlock();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
    }

    // AtomicLong[]
    private static void runWithAtomics(int accounts, long min, long max, int threads, int opsPerThread) throws InterruptedException {
        System.out.println("\n--- Version C: Atomics  ---");

        AtomicLong[] acc = new AtomicLong[accounts];
        for (int i = 0; i < accounts; i++) {
            long delta = ThreadLocalRandom.current().nextLong(max - min + 1);
            acc[i] = new AtomicLong(min + delta);
        }

        CountDownLatch latch = new CountDownLatch(threads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        for (int i = 0; i < opsPerThread; i++) {
                            int from = rnd.nextInt(accounts);
                            int to;
                            do {
                                to = rnd.nextInt(accounts);
                            } while (to == from);

                            AtomicLong a = acc[from];
                            AtomicLong b = acc[to];

                            // 1) Атомарное списание x с "from" через CAS
                            long x;
                            while (true) {
                                long fromBal = a.get();
                                if (fromBal <= 0) {
                                    x = 0;
                                    break;
                                }
                                x = rnd.nextLong(1, fromBal + 1); // 1..fromBal
                                long newFrom = fromBal - x;
                                if (a.compareAndSet(fromBal, newFrom)) {
                                    break;
                                }
                                Thread.onSpinWait();
                            }
                            if (x == 0) continue;

                            // 2) Атомарное зачисление x на "to" через CAS, с обработкой переполнения и откатом
                            int retries = 0;
                            while (true) {
                                long toBal = b.get();
                                long newTo;
                                try {
                                    newTo = Math.addExact(toBal, x);
                                } catch (ArithmeticException ex) {
                                    a.addAndGet(x);
                                    break;
                                }

                                if (b.compareAndSet(toBal, newTo)) {
                                    break;
                                }

                                if (++retries >= 64) {
                                    a.addAndGet(x);
                                    break;
                                }
                                if ((retries & 7) == 0) Thread.onSpinWait();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
    }
}
