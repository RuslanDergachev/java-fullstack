package work;

import java.util.Arrays;

public class ArrayOperations {
    /**
     * Shift array elements to the left by 'positions' using System.arraycopy.
     * Elements shifted out from the left are discarded.
     * The vacated positions on the right are filled with zeros.
     */
    public static void shiftLeftSystemCopy(int[] array, int positions) {
        if (positions <= 0 || positions >= array.length) {
            Arrays.fill(array, 0);
            return;
        }
        System.arraycopy(array, positions, array, 0, array.length - positions);
        Arrays.fill(array, array.length - positions, array.length, 0);
    }

    /**
     * Shift array elements to the left by 'positions' using manual for loop.
     * Elements shifted out from the left are discarded.
     * The vacated positions on the right are filled with zeros.
     */
    public static void shiftLeftManualLoop(int[] array, int positions) {
        if (positions <= 0 || positions >= array.length) {
            Arrays.fill(array, 0);
            return;
        }
        for (int i = 0; i < array.length - positions; i++) {
            array[i] = array[i + positions];
        }
        for (int i = array.length - positions; i < array.length; i++) {
            array[i] = 0;
        }
    }
}
