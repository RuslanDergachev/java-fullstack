package custom.tests;

import work.collectionclasses.ArrayOperations;
import work.customannotatition.Test;
import work.handmadehashmap.HandMadeHashMap;
import work.handmadehashmap.HandMadeHashMapDoubleHash;
import work.handmadelinkedlist.HandMadeLinkedList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ArrayOperationsPerformanceTest {
    private static final int[] SIZES = {1_000, 10_000, 100_000, 1_000_000};
    private static final int[] SIZES_LINKED = {1_000, 10_000, 100_000};
    private static final int[] SIZES_MAP = {10_000, 100_000, 500_000};
    private static final int[] SHIFTS = {1, 10, 100, 1000};

    @Test
    public void correctnessTest() {
        int[] base = new int[100];
        for (int i = 0; i < base.length; i++) base[i] = i + 1;

        for (int shift : SHIFTS) {
            int[] arr1 = Arrays.copyOf(base, base.length);
            int[] arr2 = Arrays.copyOf(base, base.length);

            ArrayOperations.shiftLeftSystemCopy(arr1, shift);
            ArrayOperations.shiftLeftManualLoop(arr2, shift);

            assertArrayEquals(arr1, arr2, "Mismatch for shift " + shift);
        }
    }

    @Test
    public void performanceTest() {
        System.out.println("-------------------------------------------------------");
        System.out.println("Array Shift Performance Test");
        System.out.printf("%-10s %-10s %-20s %-15s%n", "Size", "Shift", "System.arraycopy(ms)", "Manual Loop(ms)");
        System.out.println("-------------------------------------------------------");

        for (int size : SIZES) {
            int[] original = new int[size];
            for (int i = 0; i < size; i++) original[i] = i;

            for (int shift : SHIFTS) {
                if (shift >= size) continue; // skip invalid shifts

                int[] arr1 = Arrays.copyOf(original, size);
                int[] arr2 = Arrays.copyOf(original, size);

                // Measure System.arraycopy
                long start = System.currentTimeMillis();
                ArrayOperations.shiftLeftSystemCopy(arr1, shift);
                long sysTime = System.currentTimeMillis() - start;

                // Measure manual loop
                start = System.currentTimeMillis();
                ArrayOperations.shiftLeftManualLoop(arr2, shift);
                long loopTime = System.currentTimeMillis() - start;

                // Verify correctness
                assertArrayEquals(arr1, arr2, "Mismatch for size " + size + " shift " + shift);

                System.out.printf("%-10d %-10d %-20d %-15d%n", size, shift, sysTime, loopTime);
            }
        }
    }

    @Test
    public void handMadeLinkedListPerformanceTest() {
        System.out.println("-------------------------------------------------------");
        System.out.printf("%-20s %-10s %-15s %-15s%n", "ListType", "Size", "AddLast(ms)", "Get(ms)");
        System.out.println("-------------------------------------------------------");

        for (int size : SIZES_LINKED) {
            // ArrayList
            List<Integer> arrayList = new ArrayList<>();
            long start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) arrayList.add(i);
            long addTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) arrayList.get(i);
            long getTime = System.currentTimeMillis() - start;

            System.out.printf("%-20s %-10d %-15d %-15d%n", "ArrayList", size, addTime, getTime);

            HandMadeLinkedList<Integer> handmadeList = new HandMadeLinkedList<>();
            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) handmadeList.addLast(i);
            addTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) handmadeList.get(i);
            getTime = System.currentTimeMillis() - start;

            System.out.printf("%-20s %-10d %-15d %-15d%n", "HandMadeLinkedList", size, addTime, getTime);

            List<Integer> linkedList = new LinkedList<>();
            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) linkedList.add(i);
            addTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) linkedList.get(i);
            getTime = System.currentTimeMillis() - start;

            System.out.printf("%-20s %-10d %-15d %-15d%n", "LinkedList", size, addTime, getTime);
        }
    }


    @Test
    public void handMadeHashMapPerformanceTest() {
        System.out.println("-------------------------------------------------------");
        System.out.printf("%-20s %-10s %-15s %-15s%n", "MapType", "Size", "Put(ms)", "Get(ms)");
        System.out.println("-------------------------------------------------------");

        for (int size : SIZES_MAP) {
            Integer[] keys = new Integer[size];
            for (int i = 0; i < size; i++) keys[i] = i;

            // HandMadeHashMap
            HandMadeHashMap<Integer, Integer> handmadeMap = new HandMadeHashMap<>();
            long start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) handmadeMap.put(keys[i], i);
            long putTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) handmadeMap.get(keys[i]);
            long getTime = System.currentTimeMillis() - start;

            System.out.printf("%-20s %-10d %-15d %-15d%n", "HandMadeHashMap", size, putTime, getTime);

            // JDK HashMap
            HashMap<Integer, Integer> jdkMap = new HashMap<>();
            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) jdkMap.put(keys[i], i);
            putTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) jdkMap.get(keys[i]);
            getTime = System.currentTimeMillis() - start;

            System.out.printf("%-20s %-10d %-15d %-15d%n", "JDK HashMap", size, putTime, getTime);
        }
    }

    @Test
    public void handMadeHashMapDoubleHashPerformanceTest() {
        System.out.println("-------------------------------------------------------");
        System.out.printf("%-25s %-10s %-15s %-15s%n", "MapType", "Size", "Put(ms)", "Get(ms)");
        System.out.println("-------------------------------------------------------");

        for (int size : SIZES_MAP) {
            Integer[] keys = new Integer[size];
            for (int i = 0; i < size; i++) keys[i] = i;

            // HandMadeHashMapDoubleHash
            HandMadeHashMapDoubleHash<Integer, Integer> handmadeMap = new HandMadeHashMapDoubleHash<>();
            long start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) handmadeMap.put(keys[i], i);
            long putTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) handmadeMap.get(keys[i]);
            long getTime = System.currentTimeMillis() - start;

            System.out.printf("%-25s %-10d %-15d %-15d%n", "HandMadeHashMapDoubleHash", size, putTime, getTime);

            // JDK HashMap
            HashMap<Integer, Integer> jdkMap = new HashMap<>();
            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) jdkMap.put(keys[i], i);
            putTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            for (int i = 0; i < size; i++) jdkMap.get(keys[i]);
            getTime = System.currentTimeMillis() - start;

            System.out.printf("%-25s %-10d %-15d %-15d%n", "JDK HashMap", size, putTime, getTime);
        }
    }
}
