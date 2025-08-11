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

        BigInteger initial = bank.getSumOfAllAccounts();
        System.out.println("Initial total: " + initial);

        runWithSynchronized(bank, accounts, 1_000, 1_000);

        runWithLocks(bank, accounts, 1_000, 1_000);

        runWithAtomics(accounts, min, max, 1_000, 1_000);

        BigInteger fin = bank.getSumOfAllAccounts();
        System.out.println("Final total (Bank):   " + fin);
        System.out.println("Totals equal (Bank):  " + initial.equals(fin));
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
                            int from = (int) bank.pickRandomAccountId();
                            int to;
                            do {
                                to = (int) bank.pickRandomAccountId();
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

        System.out.println("Total after A: " + bank.getSumOfAllAccounts());
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
                            int from = (int) bank.pickRandomAccountId();
                            int to;
                            do {
                                to = (int) bank.pickRandomAccountId();
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

        System.out.println("Total after B: " + bank.getSumOfAllAccounts());
    }

    // AtomicLong[]
    private static void runWithAtomics(int accounts, long min, long max, int threads, int opsPerThread) throws InterruptedException {
        System.out.println("\n--- Version C: Atomics (bonus) ---");

        AtomicLong[] acc = new AtomicLong[accounts];
        for (int i = 0; i < accounts; i++) {
            long delta = ThreadLocalRandom.current().nextLong(max - min + 1);
            acc[i] = new AtomicLong(min + delta);
        }

        BigInteger initial = total(acc);
        System.out.println("Initial total (Atomic): " + initial);

        CountDownLatch latch = new CountDownLatch(threads);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            int from = ThreadLocalRandom.current().nextInt(accounts);
                            int to;
                            do {
                                to = ThreadLocalRandom.current().nextInt(accounts);
                            } while (to == from);

                            AtomicLong a = acc[from];
                            AtomicLong b = acc[to];

                            AtomicLong first = (from < to) ? a : b;
                            AtomicLong second = (from < to) ? b : a;

                            synchronized (first) {
                                synchronized (second) {
                                    long fromBal = a.get();
                                    if (fromBal <= 0) continue;

                                    long x = ThreadLocalRandom.current().nextLong(1, fromBal + 1);
                                    a.addAndGet(-x);

                                    long toBal = b.get();
                                    long newTo;
                                    try {
                                        newTo = Math.addExact(toBal, x);
                                    } catch (ArithmeticException ex) {
                                        a.addAndGet(x);
                                        continue;
                                    }
                                    b.set(newTo);
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

        BigInteger fin = total(acc);
        System.out.println("Final total (Atomic):   " + fin);
        System.out.println("Totals equal (Atomic):  " + initial.equals(fin));
    }

    private static BigInteger total(AtomicLong[] acc) {
        BigInteger sum = BigInteger.ZERO;
        for (AtomicLong a : acc) sum = sum.add(BigInteger.valueOf(a.get()));
        return sum;
    }
}
