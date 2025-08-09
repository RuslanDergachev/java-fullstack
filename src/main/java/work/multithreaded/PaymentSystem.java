package work.multithreaded;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class PaymentSystem {
    private final AtomicLong[] accounts;

    public PaymentSystem(int numberOfAccounts, long min, long max) {
        accounts = new AtomicLong[numberOfAccounts];
        for (int i = 0; i < numberOfAccounts; i++) {
            long balance = min + ThreadLocalRandom.current().nextLong(max - min + 1);
            accounts[i] = new AtomicLong(balance);
        }
    }

    public int pickRandomAccount() {
        return ThreadLocalRandom.current().nextInt(accounts.length);
    }

    public long getAccountBalance(int idx) {
        return accounts[idx].get();
    }

    public void transferAtomic(int fromIdx, int toIdx, long amount) {
        if (fromIdx == toIdx || amount <= 0) return;
        AtomicLong from = accounts[fromIdx];
        AtomicLong to = accounts[toIdx];

        // Сортируем ссылки для блокировки в одном порядке, предотвращая deadlock
        AtomicLong first = fromIdx < toIdx ? from : to;
        AtomicLong second = fromIdx < toIdx ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.get() >= amount) {
                    from.getAndAdd(-amount);
                    to.getAndAdd(amount);
                }
            }
        }
    }

    public BigInteger getTotal() {
        BigInteger sum = BigInteger.ZERO;
        for (AtomicLong account : accounts) {
            sum = sum.add(BigInteger.valueOf(account.get()));
        }
        return sum;
    }
}
