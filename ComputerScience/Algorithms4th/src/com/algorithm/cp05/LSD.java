package com.algorithm.cp05;

/**
 * 适用于定长的字符串，可以在线性时间内将其稳定排序
 */
public class LSD {

    public static void main(String[] args) {
        String[] strs = new String[]{
                "4PGC738",
                "2IYE230",
                "3CI0720",
                "1ICK750",
                "10HV845",
                "4JZY524",
                "1ICK750",
                "3CI0720",
                "10HV845",
                "10HV845",
                "2RLA629",
                "2RLA629",
                "3ATW723"
        };
        sort(strs, 7);
    }
    public static void sort(String[] a, int W) {
        int R = 256;
        int N = a.length;
        String[] aux = new String[N];

        for (int d = W - 1; d >= 0; d --) {
            int[] count = new int[R + 1];
            // count 数组的元素代表字符集，包含被排序的字符串中所有字符，本身就是已经排序好的
            // 循环完成，基于索引，可以计算出每个字符的数量。
            for (int i = 0; i < N; i ++) {
                int idx = a[i].charAt(d);
                count[idx + 1] ++;
            }

            // 分组，第一步循环已经计算出了频率
            // 因为 key 是升序的，并且 key 的值代表出现的频率，因此可以推算出值的位置
            // 比如 有 3 个 0，2 个 1 和 3 个 2，则 0 占据前 1、2、3 位，1 占据中间 4、5 位，2 的位置是 6、7、8。

            // 累加即可，如果没有对应的值就是加 0，不影响索引的变化。
            for (int r = 0; r < R; r ++) {
                count[r + 1] += count[r];
            }

            // 按照分组将原数组中的值写入到一个临时数组中
            // 拿到目标数组中的每个字符串中的当前字符，并找到其在 count 中的索引范围
            for (int i = 0; i < N; i ++) {
                int idx = count[a[i].charAt(d)]++;
                aux[idx] = a[i];
            }

            for (int i = 0; i < N; i ++) {
                a[i] = aux[i];
            }
        }
    }
}
