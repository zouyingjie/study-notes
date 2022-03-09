package com.algorithm.cp02.emsort;


/**
 * 选择排序，每次找最小值，然后和第一个值交换。
 * <p>
 * 每选择完一次，第一个一定是最小值
 */
public class SelectionSort {


    public static void main(String[] args) {
        Integer[] a = new Integer[]{3,0,8,1,2,4,9};

        sort(a);
        for (int i = 0; i < a.length; i ++) {
            System.out.println(a[i]);
        }
    }
    public static void sort(Comparable[] a) {
        int N = a.length;
        for (int i = 0; i < N - 1; i++) {
            int minIndex = i;

            // 找最小值
            for (int j = i + 1; j < N; j++) {
                if (less(a[j], a[minIndex])) {
                    minIndex = j;
                }
            }
            // 交换
            exchange(a, i, minIndex);
        }

    }

    private static boolean less(Comparable a, Comparable b) {
        return a.compareTo(b) < 0;
    }

    private static void exchange(Comparable[] a, int i, int j) {
        Comparable tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

}
