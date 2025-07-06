package work;

import java.util.HashMap;
import java.util.Map;

public class FibonacciAlgorithms {

    public static long fibonacciRecursive(int n) {
        if (n <= 1) {
            return n;
        }
        return fibonacciRecursive(n - 1) + fibonacciRecursive(n - 2);
    }

    public long fibonacciMemo(int n) {
        return fibonacciMemo(n, new HashMap<>());
    }

    private long fibonacciMemo(int n, Map<Integer, Long> cache) {
        if (n <= 1) {
            return n;
        }
        if (cache.containsKey(n)) {
            return cache.get(n);
        }
        long result = fibonacciMemo(n - 1, cache) + fibonacciMemo(n - 2, cache);
        cache.put(n, result);
        return result;
    }

    public static long fibonacciIterative(int n) {
        long a = 0, b = 1;
        for (int i = 0; i < n; i++) {
            long temp = a;
            a = b;
            b = temp + b;
        }
        return a;
    }
}
