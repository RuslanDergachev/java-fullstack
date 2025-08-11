package work.multithreaded;

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Bank {
    private final int numberOfAccounts;
    private final long minBalance;
    private final long maxBalance;

    private final ConcurrentHashMap<Integer, Long> accountsBalances = new ConcurrentHashMap<>();

    public Bank(int numberOfAccounts, long minBalance, long maxBalance) {
        if (numberOfAccounts <= 0) {
            throw new IllegalArgumentException("numberOfAccounts must be > 0");
        }
        if (minBalance > maxBalance) {
            throw new IllegalArgumentException("minBalance must be <= maxBalance");
        }
        this.numberOfAccounts = numberOfAccounts;
        this.minBalance = minBalance;
        this.maxBalance = maxBalance;

        long range = maxBalance - minBalance + 1;
        for (int i = 0; i < numberOfAccounts; i++) {
            long delta = ThreadLocalRandom.current().nextLong(range);
            long balance = minBalance + delta;
            accountsBalances.put(i, balance);
        }
    }

    public long pickRandomAccountId() {
        return ThreadLocalRandom.current().nextInt(numberOfAccounts);
    }

    public int pickRandomAccountIdInt() {
        return ThreadLocalRandom.current().nextInt(numberOfAccounts);
    }

    public long getAccountBalance(long id) {
        int idx = ensureValidAccountId(id);
        return Objects.requireNonNull(accountsBalances.get(idx), "Account does not exist");
    }

    public long getAccountBalance(int id) {
        return getAccountBalance((long) id);
    }

    public void setAccountBalance(long id, long newBalance) {
        int idx = ensureValidAccountId(id);
        accountsBalances.put(idx, newBalance);
    }

    public void setAccountBalance(int id, long newBalance) {
        setAccountBalance((long) id, newBalance);
    }

    public BigInteger getSumOfAllAcounts() {
        return accountsBalances.values()
                .stream()
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigInteger getSumOfAllAccounts() {
        return getSumOfAllAcounts();
    }

    public boolean transfer(int from, int to, long amount) {
        if (from == to || amount <= 0) return false;
        ensureValidAccountId(from);
        ensureValidAccountId(to);

        int first = Math.min(from, to);
        int second = Math.max(from, to);
        synchronized (accountsBalances.computeIfAbsent(first, k -> 0L)) {
            synchronized (accountsBalances.computeIfAbsent(second, k -> 0L)) {
                long fromBal = accountsBalances.get(from);
                if (fromBal < amount) return false;

                long toBal = accountsBalances.get(to);
                long newFrom = fromBal - amount;
                long newTo;
                try {
                    newTo = Math.addExact(toBal, amount);
                } catch (ArithmeticException ex) {
                    return false;
                }
                accountsBalances.put(from, newFrom);
                accountsBalances.put(to, newTo);
                return true;
            }
        }
    }

    private int ensureValidAccountId(long id) {
        if (id < 0 || id >= numberOfAccounts) {
            throw new IllegalArgumentException("Invalid account id: " + id);
        }
        return (int) id;
    }

    private void ensureValidAccountId(int id) {
        ensureValidAccountId((long) id);
    }
}
