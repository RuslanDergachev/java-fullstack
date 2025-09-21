package work.multithreaded;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Bank {
    private final int numberOfAccounts;
    private final long minBalance;
    private final long maxBalance;
    private Random random = new Random();

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

    public int pickRandomAccountId() {
        return random.nextInt(numberOfAccounts);
    }

    public long getAccountBalance(int id) {
        int idx = ensureValidAccountId(id);
        return Objects.requireNonNull(accountsBalances.get(idx), "Account does not exist");
    }

    public void setAccountBalance(int id, long newBalance) {
        int idx = ensureValidAccountId(id);
        accountsBalances.put(idx, newBalance);
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

    private int ensureValidAccountId(int id) {
        if (id < 0 || id >= numberOfAccounts) {
            throw new IllegalArgumentException("Invalid account id: " + id);
        }
        return id;
    }
}
