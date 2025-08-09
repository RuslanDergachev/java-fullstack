package work.multithreaded;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class Bank {
    private final int numberOfAccounts;
    private final long minBalance;
    private final long maxBalance;
    private final Random random = new Random();
    private final ConcurrentHashMap<Integer, Long> accountsBalances = new ConcurrentHashMap<>();

    public Bank(int numberOfAccounts, long minBalance, long maxBalance) {
        this.numberOfAccounts = numberOfAccounts;
        this.minBalance = minBalance;
        this.maxBalance = maxBalance;

        long range = maxBalance - minBalance + 1;
        for (int i = 0; i < numberOfAccounts; i++) {
            long balance = minBalance + (Math.abs(random.nextLong()) % range);
            accountsBalances.put(i, balance);
        }
    }

    public int pickRandomAccountId() {
        return random.nextInt(2000);
    }

    public long getAccountBalance(int accountId) {
        return accountsBalances.get(accountId);
    }

    public void setAccountBalance(int accountId, long newBalance) {
        accountsBalances.put(accountId, newBalance);
    }

    public BigInteger getSumOfAllAccounts() {
        return accountsBalances.values()
                .stream()
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public boolean transfer(int from, int to, long amount) {
        if (from == to) return false;
        if (amount <= 0) return false;

        return accountsBalances.computeIfPresent(from, (fKey, fVal) -> {
            if (fVal < amount) {
                return fVal;
            }
            long newFromVal = fVal - amount;
            accountsBalances.compute(to, (tKey, tVal) -> (tVal == null) ? amount : tVal + amount);
            return newFromVal;
        }) != null;
    }
}
