package com.algorithm.cp05;

/**
 * 键索引计数法
 *
 * 排序的依据来自于天然有序的key array
 *
 * 以要排序的目标的 key 作为索引 执行如下四步：
 *
 * 1. 统计频率：遍历目标，统计各个 key 出现的次数。
 *    假设 key 是数字，则频率数据的索引就是 0，1，2，3，4 ... 9，统计结果就是 1 的次数是 4，2 的次数是 3，3 的次数是 1，4 的次数是 4 ....
 *    count
 *
 * 2. 频率转为索引：这里就是基于统计频率，计算每个 key 的索引。简答来说就是假设我的 key 里 3 个 1，4 个 2，2 个 3，那排序结果
 *  应该是 [1，1，1，2，2，2，2，3，3]。既然我们已经统计出了 有 3 个 1，那就可以很自然的确定 1 的起始索引是 0，3 的起始索引
 *  是 1 和 2 总和，即 3 + 4 = 7；
 *
 * 3. 数据分类：有 了各个 key 的起始索引之后，我们就可以将原数组中的 值按照其 key 加到对应的索引出就行了。比如 key 是 2
 * 的值的起始索引是 3，首先就是 aux[3] = a[2]，但是因为不止有 1 个 3，下次在归类的起始索引就不应该是 3 而是 4 了，所以需要将起始索引值 + 1，
 * 最终的最大索引回事 3 + 4 -1 = 6 ，也就是 aux[3] ~ aux[6] 的值会是 2。这样一轮下来，整个 aux 就是基于 key 的有序数组了。
 *
 * 4. 回写：aux 已经是有序的了，现在将其直接写回原数组就好了。
 *
 * 由上步骤也可以看出键索引计数法有下面特征：
 *
 * 【1】非原地排序，需要占用额外的内存。
 */
class KeyIndexedCounting {
    private static final int BITS_PER_BYTE = 8;



    public static void main(String[] args) {
        GroupString[] gs = new GroupString[]{
                new GroupString("Anderson", 2),
                new GroupString("Brown", 3),
                new GroupString("Davis", 3),
                new GroupString("Garcia", 4),
                new GroupString("Harris", 1),
                new GroupString("Jackson", 3),
                new GroupString("Johnson", 4),
                new GroupString("Jones", 3),
                new GroupString("Martin", 1),
                new GroupString("Martinez", 2),
                new GroupString("Miller", 2),
                new GroupString("Moore", 1),
                new GroupString("Robinson", 2),
                new GroupString("Smith", 4),
                new GroupString("Taylor", 3),
                new GroupString("Thomas", 4),
                new GroupString("Thompson", 4),
                new GroupString("White", 2),
                new GroupString("Williams", 3),
                new GroupString("Wilson", 4),
        };

        sort(gs);
    }

    static class GroupString {
        String name;
        int group;

        public GroupString(String name, int group) {
            this.name = name;
            this.group = group;
        }

        public int key() {
            return group;
        }
    }

    public static void sort(GroupString[] a) {

        int N = a.length;
        int R = 11;
        int[] count = new int[R+1];
        GroupString[] aux = new GroupString[N];

        // 频率统计，对各个组号计数
        // count = int[]{0,0,3,5,6,6}
        for (int i = 0; i < N; i ++) {
            count[a[i].key() + 1] ++;
        }

        // 转换为索引
        // count = int[]{0,0, 3, 8, 14, 22}
        for (int r = 0; r < R; r ++) {
            count[r + 1] += count[r];
        }

        // 数据分类
        // 分组后 count 的值表示 r-1 的起始索引
        // 每次拿到后 + 1，最大值为 r 的索引 - 1
        for (int i = 0; i < N; i ++) {
            aux[count[a[i].key()] ++] = a[i];
        }

        // 回写到原数组
        for (int i = 0; i < N; i ++) {
            a[i] = aux[i];
        }

    }
}
