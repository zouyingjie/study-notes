package com.algorithm.cp02.emsort;

import edu.princeton.cs.algs4.In;

/**
 * 插入排序
 * <p>
 * 从左到有遍历，当前遍历的元素插入左边的有序数组中。
 * <p>
 * 1. 从左到右遍历数组，
 * 2. 每个元素与它左边的元素比较，如果大于，则插入
 */
public class InsertionSort {

    public static void main(String[] args) {
        Integer[] a = new Integer[]{3, 0, 8, 1, 2, 4, 9};

        sort(a);
        for (int i = 0; i < a.length; i++) {
            System.out.println(a[i]);
        }
    }

    public static void sort(Comparable[] a) {
        int N = a.length;

        for (int i = 0; i < N; i++) {
            for (int j = i; j > 0 && less(a[j], a[j - 1]); j--) {
                exchange(a, j, j - 1);
            }
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
