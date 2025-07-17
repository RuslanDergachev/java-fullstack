package work.fibonacciclasses;

import java.util.HashMap;
import java.util.Map;

public class FibonacciAlgorithms {

    public static long fibonacciRecursive(int n) {
        if (n <= 1) {
            return n;
        }
        return fibonacciRecursive(n - 1) + fibonacciRecursive(n - 2);
    }


    private static Map<Integer, Long> cache = new HashMap<>();
    public long fibonacciMemo(int n) {
        if (n <= 1) {
            return n;
        }
        if (cache.containsKey(n)) {
            return cache.get(n);
        }
        long result = fibonacciMemo(n - 1) + fibonacciMemo(n - 2);
        cache.put(n, result);
        return result;
    }

    public static long fibonacciIterative(int n) {
        if (n <= 1) {
            return n;
        }
        long prev = 0;
        long curr = 1;
        for (int i = 2; i <= n; i++) {
            long next = prev + curr;
            prev = curr;
            curr = next;
        }
        return curr;
    }
}
