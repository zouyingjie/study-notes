package com.algorithm.cp05;

import edu.princeton.cs.algs4.ST;

public class ThreeWayStringQuickSort {

    private static void sort(String[] a) {

    }

    private static void sort(String[] a, int lo, int hi, int d) {
        if (hi <= lo) {
            return;
        }

        int lt = lo;
        int gt = hi;

//        int v = charAt(a[i], d);
    }

    private static int charAt(String s, int d) {
        if (d < s.length()) {
            return s.charAt(d);
        }else {
            return -1;
        }
    }
}
